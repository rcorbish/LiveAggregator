package com.rc.agg.client;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;

/**
 * This builds up messages to send to the remote client. It is importnat that all 
 * message to the client is send via this interface. 
 * This class is exclusively for building the messages for the client.
 *
 */
public abstract class ClientCommandProcessorImpl implements ClientCommandProcessor {
	
	Logger logger = LoggerFactory.getLogger( ClientCommandProcessorImpl.class ) ;

	/**
	 * Prepare a message to be sent to the client. All messages require a command
	 * the viewName is optional as ar the args. The arguments to the command are
	 * pairs of keys and values to be printed into JSON format
	 * 
	 * @param viewName the name of the view to direct the messages to - may be null
	 * @param command the command to send to the client
	 * @param colKeys column key array, may be null
	 * @param rowKeys row key array, may be null
	 * @param description a description text - optional 
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	protected void send( String viewName, String command, String colKeys, String rowKeys, String value, String description ) throws  ClientDisconnectedException {
		StringBuilder msg = new StringBuilder( "{" ) ;
		if( viewName == null ) {
			msg.append( "\"command\":\"").append( command ).append( '"' )  ;
		} else {
			msg.append( "\"viewName\":\"").append( viewName ).
			append( "\",\"command\":\"").append( command ).append( '"' )  ;			
		}
		if( rowKeys != null ) {
			msg.append( ",\"rowKeys\": [" ).append( printArray(DataElement.splitComponents(rowKeys)) ).append( ']' );
		}
		if( colKeys != null ) {
			msg.append( ",\"colKeys\": [" ).append( printArray(DataElement.splitComponents(colKeys)) ).append( ']' );
		}
		if( description!= null ) {
			msg.append( ",\"description\": \"" ).append( description ).append( '"' );
		}
		if( value!= null ) {
			msg.append( ",\"value\": \"" ).append( value ).append( '"' );
		}
		msg.append( '}' ) ;
		transmit( msg );
	}

	
	
	protected void send( String viewName, String command ) throws ClientDisconnectedException {
		send( viewName,command, null, null, null, null ) ;
	}

	protected void send( String viewName, String command, String colKeys, String rowKeys ) throws ClientDisconnectedException {
		send( viewName,command, colKeys, rowKeys, null, null ) ;
	}

	
	protected String printArray( String arr[] ) {
		StringBuilder sb = new StringBuilder( ' ' ) ;
		for( String a : arr ) {
			sb.append( '"' ).append( a ).append( "\"," ) ;
		}
		sb.deleteCharAt( sb.length() - 1 ) ;
		return sb.toString() ;
	}
	
	
	@Override
	public void close( String viewName ) {
		try {
			send( viewName, "CLOSE" ) ;   // WTF is being closed here ?
			logger.info( "Sent close message for {}", viewName );
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
			send( null, "HEARTBEAT" ) ;
		} catch (Exception e) {
			return false ;
		}			
		return true ;
	}
	
	@Override
	public void defineView(String viewName, String columnLevels, String rowLevels, String description) throws ClientDisconnectedException {
		send( viewName, "DIM", columnLevels, rowLevels, null, description ) ;
		logger.info( "Sent DIM message for {}, cols: {}, rows: {}", viewName, DataElement.splitComponents(columnLevels), DataElement.splitComponents(rowLevels) );
	}

	@Override
	public void initializationComplete(String viewName) throws ClientDisconnectedException {
		send( viewName, "RDY" ) ;
		logger.info( "Sent RDY message for {}", viewName );
	}

	@Override
	public void reset(String viewName) throws ClientDisconnectedException {
		send( viewName, "RESET" ) ;
		logger.info( "Sent RESET message for {}", viewName );
	}

	@Override
	public void updateCell(String viewName, String columnKeys, String rowKeys, String data) throws ClientDisconnectedException {
		send( viewName, "UPD", columnKeys, rowKeys, data, null ) ;
	}

	@Override
	public void deleteCell(String viewName, String columnKeys, String rowKeys ) throws ClientDisconnectedException {
		send( viewName, "DEL", columnKeys, rowKeys ) ;
	}

	@Override
	public void deleteRow(String viewName, String rowKeys ) throws ClientDisconnectedException {
		send( viewName, "DELR", null, rowKeys ) ;
	}

	@Override
	public void deleteCol(String viewName, String columnKeys ) throws ClientDisconnectedException {
		send( viewName, "DELC", columnKeys, null ) ;
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
	protected abstract void transmit( CharSequence message ) throws ClientDisconnectedException ;
	

}