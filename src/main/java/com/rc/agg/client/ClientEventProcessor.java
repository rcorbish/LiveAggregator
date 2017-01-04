package com.rc.agg.client;

/**
 * Responsible for implementing commands from the client
 * 
 * @author richard
 *
 */
public interface ClientEventProcessor {

	
	// Client opened a new view
	public void openView( String viewName, String openRowKeys[], String openColKeys[] ) ;
	
	// Send all cells to the client
	public void resetView( String viewName ) ;
	
	// CLient closed a view
	public void closeView( String viewName ) ;

	// Notification of expanded/collapsed state for a row
	public void expandCollapseRow( String viewName, String rowKeys[], boolean expanded ) ;
	
	// Notification of expanded/collapsed state for a column
	public void expandCollapseCol( String viewName, String colKeys[], boolean expanded ) ;
	
	// Notification that view is prepared at display
	public void viewReady( String viewName ) ;
	
	// Generic request from client that requires a response
	public String [] respond( ClientMessage request ) ;
	
}
