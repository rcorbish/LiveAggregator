package com.rc.agg.client;

/**
 * Responsible for sending commands to the client. The below interface defines all mesaages
 * to go to the client
 * 
 * @author richard
 *
 */
public interface ClientCommandProcessor {

	
	// Commands TO client
	public void defineView( String viewName, String columnKeys, String rowKeys, String description ) throws ClientDisconnectedException;
	public void updateCell( String viewName, String columnKeys, String rowKeys, String data ) throws ClientDisconnectedException ;
	public void deleteCell( String viewName, String columnKeys, String rowKeys ) throws ClientDisconnectedException ;
	public void deleteRow(String viewName, String rowKeys ) throws ClientDisconnectedException ;
	public void deleteCol(String viewName, String columnKeys ) throws ClientDisconnectedException ;

	public void initializationComplete( String viewName ) throws ClientDisconnectedException ;
	public void reset( String viewName ) throws ClientDisconnectedException ;

	public void closeClient() ;
	public void close( String viewName ) ;
	
	public boolean heartbeat() throws ClientDisconnectedException ;
	
}
