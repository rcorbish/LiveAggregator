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
	 */
    void openView(String viewName) ;

	
	/**
	 * Send all cells to the client. Client forgot where it was
	 * @param viewName view name to request from server
	 */
    void resetView(String viewName) ;

	
	/**
	 * Client closed a view, client doesn't want any more updates
	 * @param viewName view name to shut down  - (hopefully this one)
	 */
    void closeView(String viewName) ;


	/**
	 * Notification that view is prepared at display. This is usually an ACK that
	 * the client opened the view
	 * @param viewName name of the view
	 */
    void viewReady(String viewName) ;

	
	/**
	 * Generic request from client that requires a response. This is for future expansion
	 * allows server to answer a client request in a generic manner. Currently, it is
	 * used to list the available views (as an example)
	 * 
	 * @param request - will be parsed by the server 
	 * @return something to send back in json to the client
	 */
    String [] respond(ClientMessage request) ;

}
