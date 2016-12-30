package com.rc.agg.client;

import java.util.List;

/**
 * Responsible for implementing commands from the client
 * 
 * @author richard
 *
 */
public interface ClientEventProcessor {

	
	// Client opened a new grid
	public void openGrid( String gridName, List<String> openRowKeys, List<String> openColKeys  ) ;
	
	// Send all cells to the client
	public void resetGrid( String gridName ) ;
	
	// CLient closed a grid
	public void closeGrid( String gridName ) ;

	// Notification of expanded/collapsed state for a row
	public void expandCollapseRow( String gridName, String rowKey, boolean expanded ) ;
	
	// Notification of expanded/collapsed state for a column
	public void expandCollapseCol( String gridName, String colKey, boolean expanded ) ;
	
	// Notification that grid is prepared at display
	public void gridReady( String gridName ) ;
	
	// Generic request from client that requires a response
	public CharSequence respond( String request ) ;
	
}
