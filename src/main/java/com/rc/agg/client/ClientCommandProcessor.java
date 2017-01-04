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
	public void defineGrid( String gridName, String columnKeys, String rowKeys, String description ) throws ClientDisconnectedException;
	public void updateCell( String gridName, String columnKeys, String rowKeys, String data ) throws ClientDisconnectedException ;
	public void deleteCell( String gridName, String columnKeys, String rowKeys ) throws ClientDisconnectedException ;
	public void deleteRow(String gridName, String rowKeys ) throws ClientDisconnectedException ;
	public void deleteCol(String gridName, String columnKeys ) throws ClientDisconnectedException ;

	public void initializationComplete( String gridName ) throws ClientDisconnectedException ;
	public void reset( String gridName ) throws ClientDisconnectedException ;

	public void closeClient() ;
	public void close( String gridName ) ;
	
	public boolean heartbeat() throws ClientDisconnectedException ;
	
}
