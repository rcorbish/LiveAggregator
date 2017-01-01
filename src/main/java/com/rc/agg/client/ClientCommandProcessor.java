package com.rc.agg.client;

/**
 * Responsible for sending commands to the client
 * 
 * @author richard
 *
 */
public interface ClientCommandProcessor {

	
	// Commands TO client
	public void defineGrid( String gridName, String columnLevels, String rowLevels, String description ) ;
	public void updateCell( String gridName, String columnKey, String rowKey, String data ) throws ClientDisconnectedException ;
	public void deleteCell( String gridName, String columnKey, String rowKey) throws ClientDisconnectedException ;
	public void deleteRow(String gridName, String rowKey) throws ClientDisconnectedException ;
	public void deleteCol(String gridName, String columnKey ) throws ClientDisconnectedException ;

	public void initializationComplete( String gridName ) ;

	public void closeClient( String gridName ) ;
	public boolean heartbeat() ;
	
}
