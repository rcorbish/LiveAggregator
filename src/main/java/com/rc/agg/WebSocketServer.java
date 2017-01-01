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

import com.rc.agg.client.ClientCommandProcessorImpl;
import com.rc.agg.client.ClientManager;
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
		clientManager.addClient( cp ) ;												// transmit needs this
		ClientProxy old = clientData.put( session, cp ) ;							// check if the map is overused - better never happen
		if( old != null ) {
			logger.warn( "EPIC FAIL: a new session accessed an old client - WTF." ) ;
		}
	}

	@OnWebSocketClose
	public void close(Session session, int statusCode, String reason) {				
		ClientProxy cp = clientData.get( session ) ;
		if( cp != null ) {
			cp.close();
			clientManager.closeClient(cp);
		} else {
			logger.warn( "Requested to close a client that does not exist - close" ) ;
		}
		clientData.remove(session);
	}

	@OnWebSocketError
	public void error(Session session, Throwable error ) {
		logger.error( "Error in connection.", error  ) ;
		ClientProxy cp = clientData.get( session ) ;
		if( cp != null ) {
			cp.close();
			clientManager.closeClient(cp);
		} else {
			logger.warn( "Requested to close a client that does not exist - error" ) ;
		}
		clientData.remove(session);
	}


	@OnWebSocketMessage
	public void message(Session session, String message) throws IOException {
		logger.info( "Received {} from client {}.", message, session.getRemoteAddress() ) ;

		ClientProxy clientProxy = clientData.get( session ) ;
		if( clientProxy == null ) {
			logger.warn( "Oh oh chongo - client proxy is not yet mapped to this session.");
		}
		
		String lines[] = message.split( "\n" ) ;
		String components[] = lines[0].split( "\\f" ) ;

		if( components.length > 1 ) {
			String gridName = components[0] ;
			String request = components[1] ;

			if( request.equals("START") ) {
				List<String> openColKeys = new ArrayList<>() ;
				List<String> openRowKeys = new ArrayList<>() ;
				for( int i=1 ; i<lines.length ; i++ ) {
					if( lines[i].startsWith("EXC\f" ) ) {
						openColKeys.add( lines[i].substring(4) ) ;
					} else if( lines[i].startsWith("EXR\f" ) ) {
						openRowKeys.add( lines[i].substring(4) ) ;
					}
				}
				logger.info("Requesting a new grid {} from the clientProxy. Currently {} clients active", gridName, clientManager.getActiveClients().size() );
				clientProxy.openGrid(gridName, openColKeys, openRowKeys);

			} else if( request.equals("STOP") ) {
				clientProxy.closeGrid(gridName);
			} else if( request.equals("EXR") && components.length>3 ) {
				String rowKey = components[2] ;
				boolean expanded = components[3].equals( "OPE" ) ;
				// Example View /f EXCO /f RowKey /f CLO  ( collapsed ) | OPE ( expanded )
				clientProxy.expandCollapseRow( gridName, rowKey, expanded ) ;
			} else if( request.equals("EXC") && components.length>3 ) {
				String colKey = components[2] ;
				boolean expanded = components[3].equals( "OPE" ) ;
				clientProxy.expandCollapseCol( gridName, colKey, expanded ) ;
			} else if( request.equals("RST")  ) {
				clientProxy.resetGrid(gridName) ;
			} else if( request.equals("RDY")  ) {
				clientProxy.gridReady(gridName) ;
			}

		} else {
			String request = lines[0] ;					
			if( request.equals("STOP") ) {
				clientProxy.close();
			} else {
				CharSequence response = clientProxy.respond( request ) ;
				if( response != null ) {	
					session.getRemote().sendString( "RSP" + "\f" + request + "\f" + response ); 
				}
			}
		}
	}

}


class WebSocketCommandProcessor extends ClientCommandProcessorImpl implements Runnable  {
	Logger logger = LoggerFactory.getLogger( WebSocketCommandProcessor.class ) ;

	// tune this in sync with the client. It should be less than the client's value
	// to prevent unnecessary timeouts.
	private static int MIN_HEARTBEAT_INTERVAL_SECONDS = 1 ;

	private Session session ;
	private BlockingQueue<String> messagesToBeSent ;
	private volatile Thread reader ;
	
	public WebSocketCommandProcessor( Session session ) {
		this.session = session ;
		messagesToBeSent = new ArrayBlockingQueue<>( 2000 ) ;
		reader = new Thread( this ) ;
		reader.start(); // This could be very dangerous - if we ever subclass this.  Make sure all vars are properly initialized
	}
	
	public void run() {
		Thread.currentThread().setName( "WSS:" + session.getRemoteAddress() );
		try {
			while( !Thread.currentThread().isInterrupted() ) {
				String message = messagesToBeSent.poll( MIN_HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS ) ;
				if( message == null ) {
					heartbeat() ;
					logger.debug( "Heartbeat sent to {}", session ) ;
				} else {
					try { 
						session.getRemote().sendString( message.toString() );
					} catch (Throwable t) {
						logger.warn("Error sending msg", t) ;
						reader = null ;
						messagesToBeSent.clear(); 
						break ;
					}
				}
			}
		} catch (InterruptedException e ) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected void transmit(CharSequence message) throws IOException, InterruptedException {
		// If reader is null - the remote client killed us :( So we won't send anything
		// there may be a few messages being sent to a killed client, but this is the
		// easiest way to manage a remote kill - just let the server send stuff until it
		// figures out it's dead
		if( reader != null ) {
			messagesToBeSent.put( message.toString() ) ;
		}
	}
}
