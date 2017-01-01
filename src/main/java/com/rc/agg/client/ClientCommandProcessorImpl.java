package com.rc.agg.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This builds up messages to send to the remote client. It is importnat that all 
 * message to the client is send via this interface. 
 * This class is exclusively for building the messages for the client.
 *
 */
public abstract class ClientCommandProcessorImpl implements ClientCommandProcessor {
	Logger logger = LoggerFactory.getLogger( ClientCommandProcessorImpl.class ) ;

	/**
	 * Prepare a message to be sent to the client.
	 * 
	 * @param messages
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	protected void send( CharSequence ... messages ) throws IOException, InterruptedException {
		StringBuilder msg = new StringBuilder( messages[0] ) ;
		for( int i=1 ; i<messages.length ;i++ ) {
			msg.append( '\f' ).append( messages[i] ) ;
		}
		transmit( msg );
	}


	@Override
	public void closeClient( String gridName ) {
		try {
			send( gridName, "CLOSE" ) ;   // WTF is being closed here ?
			logger.info( "Sent close message for {}", gridName );
		} catch (Exception ignore) {
			// Not much we can do here if we're closed anyway
		}
	}

	/**
	 * This is called to send a heartbeat to the client. It is probably called
	 * by the actual sender when no messages have been sent for a while. It could
	 * also be called in an independent thread to send periodically. The client
	 * will not react to this message (only the absence of the message)
	 */
	@Override
	public boolean heartbeat() {
		try {
			send( "HEARTBEAT" ) ;
		} catch (Exception e) {
			return false ;
		}			
		return true ;
	}
	
	@Override
	public void defineGrid(String gridName, String columnLevels, String rowLevels, String description) {
		try {
			send( gridName, "DIM", columnLevels, rowLevels, description ) ;
			logger.info( "Sent DIM message for {}", gridName );
		} catch (Exception e) {
			logger.error( "Error sending DIM message", e );
			closeClient( gridName ) ;
		}			
	}

	@Override
	public void initializationComplete(String gridName) {
		try {
			send( gridName, "RDY" ) ;
			logger.info( "Sent RDY message for {}", gridName );
		} catch (Exception e) {
			logger.error( "Error sending RDY message", e );
			closeClient( gridName ) ;
		}			
	}


	@Override
	public void updateCell(String gridName, String columnKey, String rowKey, String data) throws ClientDisconnectedException {
		try {
			send( gridName, "UPD", columnKey, rowKey, data ) ;
		} catch (Exception e) {
			logger.error( "Error sending UPD message", e );
			closeClient( gridName ) ;
			throw new ClientDisconnectedException() ;
		}			
	}

	@Override
	public void deleteCell(String gridName, String columnKey, String rowKey) throws ClientDisconnectedException {
		try {
			send( gridName, "DEL", columnKey, rowKey ) ;
		} catch (Exception e) {
			logger.error( "Error sending DEL message", e );
			closeClient( gridName ) ;
			throw new ClientDisconnectedException() ;
		}			
	}

	@Override
	public void deleteRow(String gridName, String rowKey) throws ClientDisconnectedException {
		try {
			send( gridName, "DELR", rowKey ) ;
		} catch (Exception e) {
			logger.error( "Error sending DELR message", e );
			closeClient( gridName ) ;
			throw new ClientDisconnectedException() ;
		}			
	}

	@Override
	public void deleteCol(String gridName, String columnKey ) throws ClientDisconnectedException {
		try {
			send( gridName, "DELC", columnKey ) ;
		} catch (Exception e) {
			logger.error( "Error sending DELC message", e );
			closeClient( gridName ) ;
			throw new ClientDisconnectedException() ;
		}			
	}

/**
 * To actually transmit data to the client we will need a real implementation of this.
 * For example , we can use JMS, websockets, EventSources etc.
 * The exceptions indicate failuref - a proper failure that requires a client disconnect.
 * Other exceptions should be handled - if they are not considered critical.
 * 
 * @param message
 * @throws IOException
 * @throws InterruptedException
 */
	protected abstract void transmit( CharSequence message ) throws IOException, InterruptedException ;
	

}