package com.rc.agg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.rc.agg.client.ClientCommandProcessorImpl;
import com.rc.agg.client.ClientDisconnectedException;
import com.rc.agg.client.ClientManager;
import com.rc.agg.client.ClientMessage;
import com.rc.agg.client.ClientProxy;


@WebSocket
public class WebSocketServer  {
	Logger logger = LoggerFactory.getLogger( WebSocketServer.class ) ;
	
	static ClientManager clientManager ;	// keeps info about each client with an open view
	
    private static final Map<Session,ClientProxy> clientData = new ConcurrentHashMap<>() ;
    
	@OnWebSocketConnect
	public void connect( Session session )  {
		logger.info( "Opened connection to {}", session.getRemote() ) ;
		WebSocketCommandProcessor wscp = new WebSocketCommandProcessor(session) ;	// keep tabs on the rempote client
		ClientProxy cp = new ClientProxy(clientManager, wscp ) ;					// maintain a proxy to the client - used for sending messages
		wscp.setClientProxy( cp ) ;
		ClientProxy old = clientData.put( session, cp ) ;							// check if the map is overused - better never happen
		if( old != null ) {
			logger.warn( "EPIC FAIL: a new session accessed an old client - WTF." ) ;
		}		
	}

	@OnWebSocketClose
	public void close(Session session, int statusCode, String reason) {				
		ClientProxy cp = clientData.get( session ) ;
		if( cp != null ) {
			logger.info( "Remote session {} requested close, reason: {}", session.getRemoteAddress(), reason ) ; 
			cp.close();
		} else {
			logger.warn( "Requested to close a client that does not exist - wss close event" ) ;
		}
		clientData.remove(session);
	}

	@OnWebSocketError
	public void error(Session session, Throwable error ) {
		logger.info( "Remote session {} detected error.", session.getRemoteAddress(), error ) ; 
		ClientProxy cp = clientData.get( session ) ;
		if( cp != null ) {
			cp.close();
		} else {
			logger.warn( "Requested to close a client that does not exist - wss error event" ) ;
		}
		clientData.remove(session);
	}


	@OnWebSocketMessage
	public void message(Session session, String message) throws IOException {
		logger.info( "Received {} from {}.", message, session.getRemoteAddress() ) ;
		
		ClientProxy clientProxy = clientData.get( session ) ;
		if( clientProxy == null ) {
			logger.warn( "Oh oh chongo - client proxy is not yet mapped to this session.");
			return ;
		}
		
		ClientMessage clientMessage = new Gson().fromJson( message, ClientMessage.class ) ;
		
		if( clientMessage.command.equals("START") ) {
			logger.info("Requesting a new grid {} from the clientProxy. Currently {} clients active", clientMessage.gridName, clientManager.getActiveClients().size() );
			clientProxy.openGrid(clientMessage.gridName, clientMessage.colKeys, clientMessage.rowKeys );

		} else if( clientMessage.command.equals("STOP") ) {
			if( clientMessage.gridName != null ) {
				clientProxy.closeGrid(clientMessage.gridName);
			} else {
				clientProxy.close();
			}
		} else if( clientMessage.command.equals("EXR")  ) {
			clientProxy.expandCollapseRow( clientMessage.gridName, clientMessage.rowKeys, true ) ;
		} else if( clientMessage.command.equals("COR")  ) {
			clientProxy.expandCollapseRow( clientMessage.gridName, clientMessage.rowKeys, false ) ;
		} else if( clientMessage.command.equals("EXC")  ) {
			clientProxy.expandCollapseCol( clientMessage.gridName, clientMessage.colKeys, true ) ;
		} else if( clientMessage.command.equals("COC")  ) {
			clientProxy.expandCollapseCol( clientMessage.gridName, clientMessage.colKeys, false ) ;
		} else if( clientMessage.command.equals("RST")  ) {
			clientProxy.resetGrid(clientMessage.gridName) ;
		} else if( clientMessage.command.equals("RDY")  ) {
			clientProxy.gridReady(clientMessage.gridName) ;
		} else if( clientMessage.command.equals("STOP")  ) {
			clientProxy.gridReady(clientMessage.gridName) ;
		} else {
			String responses[] = clientProxy.respond( clientMessage ) ;
			if( responses != null ) {	
				StringBuilder sb = new StringBuilder( "{\"command\":\"" )
						.append( clientMessage.command )
						.append("\",\"responses\": [ " ) ;
				for( String response : responses ) {
					sb.append( '"' ).append( response ).append( "\"," ) ;
				}
				sb.deleteCharAt( sb.length()-1 ) ;
				sb.append( "]}" ) ;
				
				session.getRemote().sendString( sb.toString() ) ; 
			}
		}
	}
	
	public static String toStringStatic() {
		String rc = "" ;
		int i = 0 ;
		for( Session s : clientData.keySet() ) {
			i++ ;
			rc += ( "\nActive client #" + i + " connected to " + s.getRemoteAddress() ) ;
		}
		return rc ;
	}
}


class WebSocketCommandProcessor extends ClientCommandProcessorImpl implements Runnable  {
	Logger logger = LoggerFactory.getLogger( WebSocketCommandProcessor.class ) ;

	// tune this in sync with the client. It should be less than the client's value
	// to prevent unnecessary timeouts.
	private static int MIN_HEARTBEAT_INTERVAL_SECONDS = 1 ;

	private Session session ;
	private ClientProxy clientProxy ;
	private BlockingQueue<String> messagesToBeSent ;
	private volatile Thread reader ;
	
	public WebSocketCommandProcessor( Session session ) {
		this.session = session ;
		messagesToBeSent = new ArrayBlockingQueue<>( 2000 ) ;
		reader = new Thread( this ) ;
		reader.start(); // This could be very dangerous - if we ever subclass this.  Make sure all vars are properly initialized
	}

	public void setClientProxy( ClientProxy clientProxy ) {
		this.clientProxy = clientProxy ;
	}
	
	@Override
	public void closeClient( String gridName ) {
		if( reader != null ) {
			reader.interrupt(); 
		}
		super.closeClient( gridName ) ;
	}
	
	/**
	 * Pull messages off the outbound queue, and send directly to the client. If 
	 * nothing has been requested for a while, send a heartbeat.
	 * This thread is terminated by an interrupt ( from closeClient() )
	 * or because of a message sending error.
	 * 
	 *  @see closeClient()
	 */
	public void run() {
		Thread.currentThread().setName( "WSS:" + session.getRemoteAddress() );
		try {
			while( !Thread.currentThread().isInterrupted() ) {
				String message = messagesToBeSent.poll( MIN_HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS ) ;
				if( message == null ) {
					heartbeat() ;
					logger.debug( "Heartbeat sent to {}", session ) ;
				} else {
					session.getRemote().sendString( message );
				}
			}
		} catch (InterruptedException t) {
			// ignore - this is an expected exception :(
		} catch (Throwable t) {
			logger.warn("Sending msg stopped by xmit error.", t ) ;
		}
		
		clientProxy = null ;
		reader = null ;
		messagesToBeSent = null ;
	}
	
	@Override
	protected void transmit(CharSequence message) throws ClientDisconnectedException {
		if( messagesToBeSent != null ) {		// must not put a message on the queue if there's no queue (i.e. reader died unexpectedly)
			try {
				messagesToBeSent.put( message.toString() ) ;
			} catch( InterruptedException e ) {
				logger.info( "Interrupted during wait to transmit to {}.", session.getRemoteAddress() ) ;
			}
		} else {
			logger.info( "Refused to send {} to terminating client {}.", message, session.getRemoteAddress() ) ;			
			throw new ClientDisconnectedException() ;
		}
	}
	
	public String toString() {
		
		return "WebSocket to " + session.getRemoteAddress() + "\n" +
				messagesToBeSent.size()  + " pending messages \n" + 
				"Reader is " + ( (reader==null) ? "dead\n" : "alive\n" ) ;
	}
}



