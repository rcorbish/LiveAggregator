package com.rc.agg.client;

/**
 * Responsible for implementing commands from the client
 * 
 * @author richard
 * 
 * @see ClientCommandProcessorImpl
 */
public interface ClientEventProcessor {


	/**
	 * Client opened a new view
	 * 
	 * @param viewName		What did client open
	 * @param openRowKeys	what row keys are expanded ( rowkey1\trowkey2\t .... )
	 * @param openColKeys	what col keys are expanded
	 */

	public void openView( String viewName, String openRowKeys[], String openColKeys[] ) ;

	/**
	 * Send all cells to the client. Client forgot where it was
	 * @param viewName view name to request from server
	 */
	public void resetView( String viewName ) ;

	/**
	 * Client closed a view, client doesn't want any more updates
	 * @param viewName view name to shut down  - (hopefully this one)
	 */
	public void closeView( String viewName ) ;

	/**
	 *  Notification of expanded/collapsed state for a row. Client
	 *  clicked on a +/- row button to change the state of a <b>single</b> 
	 *  row
	 *  
	 * @param viewName	the usual - represents which view 
	 * @param rowKeys the compnd rowKey ( tab separated ) 
	 * @param expanded true==expanded otherwise client just closed the row
	 */
	public void expandCollapseRow( String viewName, String rowKeys[], boolean expanded ) ;

	/**
	 * Notification of expanded/collapsed state for a column. Client
	 * clicked on a +/- col button to change the state of a <b>single</b> 
	 * col
	 *  
	 * @param viewName	the usual - represents which view 
	 * @param colKeys the compnd colKey ( tab separated ) 
	 * @param expanded true==expanded otherwise client just closed the col
	 */
	public void expandCollapseCol( String viewName, String colKeys[], boolean expanded ) ;

	/**
	 * Notification that view is prepared at display. This is usualy an ACK that
	 * the client opened the view
	 * @param viewName name of the view
	 */
	public void viewReady( String viewName ) ;

	/**
	 * Generic request from client that requires a response. This is for future expansion
	 * allows server to answer a client request in a generic manner. Currently it is
	 * ised to list the available views (as an example)
	 * 
	 * @param request - will be parsed by the server 
	 * @return something to send back in json to the client
	 */
	public String [] respond( ClientMessage request ) ;

}
