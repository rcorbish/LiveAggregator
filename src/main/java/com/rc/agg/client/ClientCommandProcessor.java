package com.rc.agg.client;

/**
 * Responsible for sending commands to the client
 * 
 * @author richard
 *
 */
public interface ClientCommandProcessor {

	
	// Commands TO client
	public void defineGrid( String gridName, String columnKeys, String rowKeys, String description ) ;
	public void updateCell( String gridName, String columnKeys, String rowKeys, String data ) throws ClientDisconnectedException ;
	public void deleteCell( String gridName, String columnKeys, String rowKeys ) throws ClientDisconnectedException ;
	public void deleteRow(String gridName, String rowKeys ) throws ClientDisconnectedException ;
	public void deleteCol(String gridName, String columnKeys ) throws ClientDisconnectedException ;

	public void initializationComplete( String gridName ) ;

	public void closeClient( String gridName ) ;
	
	public boolean heartbeat() ;
	
}
