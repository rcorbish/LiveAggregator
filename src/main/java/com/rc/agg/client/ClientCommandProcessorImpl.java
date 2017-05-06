package com.rc.agg.client;

import java.io.IOException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;

/**
 * This builds messages to send to the remote client. It is importnat that all 
 * message to the client is send via this class. 
 * This class is exclusively for building the messages for the client. It 
 * is the transport of the protocol. It turns intent into a signal to remote client
 *
 */
public abstract class ClientCommandProcessorImpl implements ClientCommandProcessor {
	
	final static Logger logger = LoggerFactory.getLogger( ClientCommandProcessorImpl.class ) ;

	/**
	 * Prepare a message to be sent to the client. All messages require a command,
	 * the viewName is optional, as are the args. The arguments to the command are
	 * pairs of keys and values to be printed in JSON format
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

	
	/**
	 * This is a special case of the send Method
	 * 
	 * @see #send(String, String, String, String, String, String)
	 */
	protected void send( String viewName, String command ) throws ClientDisconnectedException {
		send( viewName,command, null, null, null, null ) ;
	}

	/**
	 * This is a special case of the send Method
	 * 
	 * @see #send(String, String, String, String, String, String)
	 */
	protected void send( String viewName, String command, String colKeys, String rowKeys ) throws ClientDisconnectedException {
		send( viewName,command, colKeys, rowKeys, null, null ) ;
	}

	/**
 	 * Used to print a String array into json compatible format.
	 * 
	 * e.g. in[0] = 'abc'
	 * 		in[1] = 'def'
	 * 		in[2] = 'ghi'
	 * 		out = "[ 'abc', 'def', 'ghi']"
	 * 
	 * @param arr the arr of Strings to print
	 * @return one String representing the array
 	 */
	public static String printArray( String arr[] ) {
		StringBuilder sb = new StringBuilder( ' ' ) ;
		for( String a : arr ) {
			sb.append( '"' ).append( a ).append( "\"," ) ;
		}
		sb.deleteCharAt( sb.length() - 1 ) ;
		return sb.toString() ;
	}
	/**
	 * @see #printArray(String[])
	 * @param arr
	 * @return one String representing the array
	 */
	public static String printArray( Iterable<String> arr ) {
		StringBuilder sb = new StringBuilder( ' ' ) ;
		for( String a : arr ) {
			sb.append( '"' ).append( a ).append( "\"," ) ;
		}
		sb.deleteCharAt( sb.length() - 1 ) ;
		return sb.toString() ;
	}
	
	
	/**
	 * Shutdown one the named client view
	 * 
	 * @param viewName the unique name for the view
	 */
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
	
	/**
	 * Sends the first message to a newly opened view. It indicates
	 * what the rows and columns of the view are.
	 * 
	 * @param viewName the unique ID of the view (used in later messaging)
	 * @param columnLevels the tab separated list of column attribute names
	 * @param rowLevels the tab separated list of row attribute names
	 * @param description a nice description for the view (for display)
	 */
	@Override
	public void defineView(String viewName, String columnLevels, String rowLevels, String description) throws ClientDisconnectedException {
		send( viewName, "DIM", columnLevels, rowLevels, null, description ) ;
		logger.info( "Sent DIM message for {}, cols: {}, rows: {}", viewName, DataElement.splitComponents(columnLevels), DataElement.splitComponents(rowLevels) );
	}

	/**
	 * Notify the view that we have completed all initialization on the server 
	 * The view will be prepared to receive updates, and possibly redraw itself
	 * @param viewName the name of the view (from defineView)
	 */
	@Override
	public void initializationComplete(String viewName) throws ClientDisconnectedException {
		send( viewName, "RDY" ) ;
		logger.info( "Sent RDY message for {}", viewName );
	}

	/**
	 * Notify the client to request a full redraw of the view (usu. because the 
	 * view definition was changed)
	 * @param viewName the name of the view (from defineView)
	 */
	@Override
	public void reset(String viewName) throws ClientDisconnectedException {
		send( viewName, "RESET" ) ;
		logger.info( "Sent RESET message for {}", viewName );
	}

	/**
	 * Update the value of a named cell. A cell is named by the attribute Values 
	 * of the rows and columns. So if a view is CCY and GENDER x DATE, a cell key may be 
	 * USD\tMALE\f2017-12-25.  Data an be anything, but the samples all expect a numeric
	 * value ( may be in accounting format )
	 * 
	 * @param viewName as always identify which client view
	 * @param columnKeys the column key (attribute values - tab separated)
	 * @param rowKeys the row key (attribute values - tab separated)
	 * @param data this is expected to be a nicely formatted numeric value
	 */
	@Override
	public void updateCell(String viewName, String columnKeys, String rowKeys, String data) throws ClientDisconnectedException {
		send( viewName, "UPD", columnKeys, rowKeys, data, null ) ;
	}

	/**
	 * Delete an single cell, used when a cell is marked unused (e.g. value == 0.0 )
	 * 
	 * @param viewName as always identify which client view
	 * @param columnKeys the column key (attribute values - tab separated)
	 * @param rowKeys the row key (attribute values - tab separated)
	 */
	@Override
	public void deleteCell(String viewName, String columnKeys, String rowKeys ) throws ClientDisconnectedException {
		send( viewName, "DEL", columnKeys, rowKeys ) ;
	}

	/**
	 * Delete an entire row, used when the row is collapsed
	 * 
	 * @param viewName as always identify which client view
	 * @param rowKeys the row key (attribute values - tab separated)
	 */
	@Override
	public void deleteRow(String viewName, String rowKeys ) throws ClientDisconnectedException {
		send( viewName, "DELR", null, rowKeys ) ;
	}

	/**
	 * Delete an entire column, used when the row is collapsed
	 * 
	 * @param viewName as always identify which client view
	 * @param columnKeys the column key (attribute values - tab separated)
	 */
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