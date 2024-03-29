package com.rc.agg;

import com.google.gson.Gson;
import com.rc.agg.client.ClientCommandProcessorImpl;
import com.rc.agg.client.ClientDisconnectedException;
import com.rc.agg.client.ClientMessage;
import com.rc.agg.client.ClientProxy;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One of the more interesting classes, this handles raw messages from the client.
 * It does delegate most work to the private class (below). There is one WebSocketServer
 * per server instance, each client instance has a WebSocketCommandProcessor to 
 * manage itself. We have 2 lists of clients: 1 in client manager and one in clientData. Ideally these
 * need to be merged. TODO all that. The difference is that the client manager handles messages
 * from the web page and the clientData handles messages to - pretty lame
 * 
 * @author richard
 *
 */
@WebSocket
public class WebSocketServer  {
	final static Logger logger = LoggerFactory.getLogger( WebSocketServer.class ) ;


    private static final Map<Session,ClientProxy> clientData = new ConcurrentHashMap<>() ;
    
	@OnWebSocketConnect
	public void connect( Session session )  {
		logger.info( "Opened connection to {} {}", session.getRemoteAddress(), session.getUpgradeRequest().getHeaders( "User-Agent" ) ) ;
		WebSocketCommandProcessor wscp = new WebSocketCommandProcessor(session) ;	// keep tabs on the remote client
		ClientProxy cp = new ClientProxy( wscp ) ;					// maintain a proxy to the client - used for sending messages
		ClientProxy old = clientData.put( session, cp ) ;							// check if the map is overused - better never happen
		if( old != null ) {
			logger.warn( "EPIC FAIL: a new session accessed an old client - WTF." ) ;
		}		
	}

	@OnWebSocketClose
	public void close(Session session, int ignoredStatusCode, String reason) {
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
			logger.warn( "Remote session {} error, reason: {}", session.getRemoteAddress(), error.getMessage() ) ; 
			cp.close();
		} else {
			logger.warn( "Requested to close a client that does not exist - wss error event" ) ;
		}
		clientData.remove(session);
	}


	/**
	 * This parses the client message and then hands it off to a client processor. It's 
	 * semi-dumb (which is never good) 
	 * 
	 */
	@OnWebSocketMessage
	public void message(Session session, String message) throws IOException {
		logger.debug( "Received {} from {}.", message, session.getRemoteAddress() ) ;
		
		ClientProxy clientProxy = clientData.get( session ) ;
		if( clientProxy == null ) {
			// Did we receive a message on an uninitialized session?
			// ignore it
			logger.warn( "Oh oh chongo - uninitialized client {} ignored message {}.", session.getRemoteAddress(), message );
			return ;
		}
		
		ClientMessage clientMessage = new Gson().fromJson( message, ClientMessage.class ) ;

        switch (clientMessage.command) {
            case "START" -> clientProxy.openView(clientMessage.viewName);
			case "RST" -> clientProxy.resetView(clientMessage.viewName);
			case "RDY" -> clientProxy.viewReady(clientMessage.viewName);
			case "RATE" -> clientProxy.setRate(clientMessage.rate);
            case "STOP" -> {
                if (clientMessage.viewName != null) {
                    clientProxy.closeView(clientMessage.viewName);
                } else {
                    clientProxy.close();
                }
            }
            default -> {
                String[] responses = clientProxy.respond(clientMessage);
                if (responses != null) {
                    StringBuilder sb = new StringBuilder("[{\"command\":\"")
                            .append(clientMessage.command)
                            .append("\",\"responses\": [ ");
                    for (String response : responses) {
                        sb.append('"').append(response).append("\",");
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    sb.append("]}]");

                    session.getRemote().sendString(sb.toString());
                }
            }
        }
	}
	
	/**
	 * Used for debugging in the monitor page only
	 * @return a representation of the static parts of the class
	 */
	public static String toStringStatic() {
		StringBuilder rc = new StringBuilder() ;
		int i = 0 ;
		for( var entry : clientData.entrySet() ) {
			i++ ;
			rc.append( "\nActive client #" )
				.append( i )
				.append( " connected to " )
				.append( entry.getKey().getRemoteAddress() )
				.append( '\n' )
				.append( entry.getKey().getUpgradeRequest().getHeaders( "User-Agent" ) )
				.append( '\n' ) ;
			rc.append( entry.getValue() ).append('\n') ;
		}
		return rc.toString() ;
	}
}

/**
 * One of these is created for each client web-page. It handles the transmission to the 
 * server. The transmission is separated from the caller using a queue. Transmit adds a message to that
 * queue. A different thread reads the queue and sends bunches of messages to the client
 * 
 * @author richard
 *
 */
class WebSocketCommandProcessor extends ClientCommandProcessorImpl implements Runnable  {
	static Logger logger = LoggerFactory.getLogger( WebSocketCommandProcessor.class ) ;

	private final static int MAX_RATE = 5 ;
	// tune this in sync with the client. It should be less than the client's value
	// to prevent unnecessary timeouts.
	private static final int MIN_HEARTBEAT_INTERVAL_SECONDS = 1 ;
	private static final int CLIENT_MESSAGE_SENDING_INTERVAL_MILLIS = 150 ;

	private final Session session ;
	private final BlockingQueue<String> messagesToBeSent ;
	private Thread reader ;

	private int rate ;

	public WebSocketCommandProcessor( Session session ) {
		this.session = session ;
		messagesToBeSent = new ArrayBlockingQueue<>( 2000 ) ;
		reader = new Thread( this ) ;
		rate = 1 ;
		reader.start(); // This could be very dangerous - if we ever subclass this.  Make sure all vars are properly initialized
	}

	
	@Override
	public void closeClient() {
		logger.info( "Closing entire client {}", session.getRemoteAddress() ) ;
		Thread readerCopy = reader ;	// in case the thread dies after we checked for null !
		if( readerCopy != null ) {
			readerCopy.interrupt();
		}
	}

	public void setRate( int rate ) {
		if( rate > MAX_RATE ) rate = MAX_RATE ;
		if( rate < 0 ) rate = 0 ;
		this.rate = rate;
	}
	/**
	 * Pull messages off the outbound queue, and send directly to the client. If 
	 * nothing has been requested for a while, send a heartbeat.
	 * This thread is terminated by an interrupt ( from closeClient() )
	 * or because of a message sending error.
	 * 
	 */
	public void run() {
		Thread.currentThread().setName( "WSS:" + session.getRemoteAddress() );
		StringBuilder msgBuffer = new StringBuilder( 100_000 ) ;
		List<String> messagesToSend = new ArrayList<>(16) ; 
		try {
			long nextHeartbeatMsg = 0 ;
			int rateClock = 1 ;
			while( !Thread.currentThread().isInterrupted() ) {
				Thread.sleep( CLIENT_MESSAGE_SENDING_INTERVAL_MILLIS ) ;
				if( messagesToBeSent.isEmpty() ) {
					if( System.currentTimeMillis() > nextHeartbeatMsg) {
						heartbeat();
						logger.debug( "Heartbeat sent to {}", session ) ;
					}
					continue;
				}
				if( rate == 0 ) continue ;
				// Count down from rate to 0 before sending so rate 5 is slow
				rateClock-- ;
				if( rateClock>0 ) continue;
				rateClock = MAX_RATE - rate ;
				messagesToBeSent.drainTo( messagesToSend ) ;
				nextHeartbeatMsg = System.currentTimeMillis() + MIN_HEARTBEAT_INTERVAL_SECONDS*1000 ;
				msgBuffer.setLength( 0 ) ;
				msgBuffer.append( '[' ) ;
				for( String message : messagesToSend ) {
					msgBuffer.append( message ) ;
					msgBuffer.append( ',' ) ;
				}
				msgBuffer.setCharAt(msgBuffer.length()-1,  ']' ) ;
				session.getRemote().sendString( msgBuffer.toString() );
				messagesToSend.clear();
			}
		} catch (InterruptedException t) {
			// ignore - this is an expected exception :(
		} catch (Throwable t) {
			logger.warn("Sending msg stopped by transmit error.", t ) ;
		}
		
		reader = null ;
	}
	
	@Override
	protected void transmit(CharSequence message) throws ClientDisconnectedException {
		if( reader != null ) {		// must not put a message on the queue if there's no queue (i.e. reader died unexpectedly)
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
	
	/**
	 * Used for the monitor & debugging
	 * 
	 * @return a text representing the instance
	 */
	public String toString() {		
		return "WebSocket to " + session.getRemoteAddress() + "\n" +
				messagesToBeSent.size()  + " pending messages \n" + 
				"Reader is " + ( (reader==null) ? "dead\n" : "alive\n" ) +
				"Agent is " + session.getUpgradeRequest().getHeaders( "User-Agent" ) + "\n";

	}
}



