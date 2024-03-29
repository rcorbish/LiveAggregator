package com.rc.agg.client;

/**
 * Responsible for sending commands to the client. The below interface defines all messages
 * to go to the client.
 * 
 * Implementations should transmit the intent over a useful medium to the client
 * 
 * @author richard
 *
 */
public interface ClientCommandProcessor {
	//===========================
	// Commands TO client
	//===========================

	/**
	 * Define the row and column headings of a view. Sent as 1st
	 * response when a view is opened.
	 */
    void defineView(String viewName, String columnKeys, String rowKeys, String description) throws ClientDisconnectedException;
	/**
	 * This sends the request to update a cell's contents in a view`'
	 */
    void updateCell(String viewName, String columnKeys, String rowKeys, String data) throws ClientDisconnectedException ;
	/**
	 * When a cell is marked unused, this requests the view to remove it from View.
	 * May be removed in future releases
	 */
    void deleteCell(String viewName, String columnKeys, String rowKeys) throws ClientDisconnectedException ;
	/**
	 * Used as part of the expand and collapse (more collapse), to remove an empty row
	 */
    void deleteRow(String viewName, String rowKeys) throws ClientDisconnectedException ;
	/**
	 * Used as part of the expand and collapse (more collapse), to remove an empty col
	 */
    void deleteCol(String viewName, String columnKeys) throws ClientDisconnectedException ;

	/**
	 * Notify client the view is fully ready to be processed,
	 */
    void initializationComplete(String viewName) throws ClientDisconnectedException ;
	/**
	 * When the view definition changes - causes the client to force redraw
	 */
    void reset(String viewName) throws ClientDisconnectedException ;

	/**
	 * Notify client that the server forced close of the whole client
	 */
    void closeClient() ;
	/**
	 * Notify client that the server forced close of a single view
	 */
    void close(String viewName) ;
	
	/**
	 * Send a heartbeat message to the client if nothing's been sent for a while
	 */
    boolean heartbeat() throws ClientDisconnectedException ;

	void setRate( int rate ) ;
}
