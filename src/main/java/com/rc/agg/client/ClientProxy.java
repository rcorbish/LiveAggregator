package com.rc.agg.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.dataview.ClientDataView;
import com.rc.dataview.DataElementDataView;
import com.rc.dataview.DataElementStore;

/**
 * This handles all messages <b>from</b> the client. Interprets the
 * message and makes a request to the ClientDataView which keeps 
 * client state. Outbound messages are sent <b>to</b> the client
 * in the ClientCommandProcessor. [Could these two be merged?]
 * 
 * 
 * @see ClientCommandProcessor .
 * 
 * @author richard
 *
 */
public class ClientProxy implements ClientEventProcessor {

	final static Logger logger = LoggerFactory.getLogger( ClientProxy.class ) ;

	private final Map<String,ClientDataView> openDataViews ;
	private final ClientCommandProcessor clientCommandProcessor ;

	public ClientProxy( final ClientCommandProcessor clientCommandProcessor ) {
		openDataViews = new ConcurrentHashMap<>();
		this.clientCommandProcessor = clientCommandProcessor;
	}

	/**
	 * Respond to a request sent by the client to get info about the system (e.g. tell
	 * me available views, I am stopping, etc. ). 
	 * 
	 * These messages <b>may</b> be global in scope and not view related depending on the 
	 * presence of viewId.
	 * 
	 * @param request - the actual message
	 * 
	 * @return The response message to send back to the client - null implies no response
	 */
	public String [] respond( ClientMessage request ) {
		String[] rc = null ;
		try {
            switch (request.command) {
                case "STOP" ->        // shutdown the whole client
                    // Stoppage - no reply necessary, but just in case prepare one...
                        rc = new String[]{"OK"};
                case "START" ->
                    // Something started - assume OK. Any failure will not send a message, the client must time out
                        rc = new String[]{"OK"};
                case "VIEWS" -> {        // list available views
                    // Answer with the list of available views that can be shown on the client desktop
                    Collection<String> vdns = DataElementStore.getInstance().getDataViewNames();
                    rc = new String[vdns.size()];
                    int i = 0;
                    for (String vdn : vdns) {
                        rc[i++] = vdn;
                    }
                    Arrays.sort(rc);  // let's be nice - sort 'em
                }
                case "ATTRIBUTES" ->
                    // *************************
                    // BAD BAD BAD - need to fix this ASAP
                    // *************************
                        rc = new String[]{"BOOK", "CCY", "TRADEID"};
				default -> throw new IllegalStateException("Unexpected value: " + request.command);
			}
		} catch( Exception ex ) {
			logger.error( "Error responding to request.", ex ) ;
		}
		return rc ;
	}

	@Override
	public void viewReady(String viewName) {
		ClientDataView dgp = openDataViews.get(viewName) ;
		if( dgp == null ) {
			logger.info( "Ignoring view ready info for unknown view: '{}'.", viewName  ) ;
		} else {
			logger.info( "Client confirmed view {} ready.", viewName  ) ;
		}
	}


	// Client closed a view, remove from active list
	public void closeView( String viewName ) {
		ClientDataView cdv = openDataViews.remove(viewName) ;
		if( cdv == null ) {
			logger.warn( "Cannot find {} in the openViews.", viewName );
		} else {
			cdv.close();
			logger.info( "Removed '{}' from openViews. {} views remain.", viewName, openDataViews.size() );
		}
	}

	// Client requested complete refresh of a view. Send everything to 
	// client as an UPDate
	public void resetView( String viewName ) {
		logger.info( "Resetting view {}", viewName ) ;
		ClientDataView dgp = openDataViews.get(viewName) ;
		if( dgp == null ) {
			logger.warn( "Cannot find {} in the openViews.", viewName );
		} else {
			dgp.sendAll() ;
			try {
				clientCommandProcessor.initializationComplete(viewName);
				logger.info( "Reset view {} completed.", viewName ) ;
			} catch( ClientDisconnectedException cde ) {
				logger.error( "Failed to reset remove view " + viewName, cde );
			}
		}
	}

	/**
	 * Sets the update rate of all views, 0 = pause, 1 = slow, 2 = medium, etc.
	 * @param rate
	 */
	public void setRate(  int rate ) {
		logger.info( "Changing rate to {}", rate ) ;
		clientCommandProcessor.setRate( rate ) ;
	}

	/**
	 * When opening a new view we need to set some flags on other components
	 * that we are active and ready to receive process events
	 * Also we set the current expand/collapse state of our view, add the view
	 * to active views and  ask for the current state to be sent to the client
	 * 
	 */
	public void openView( String viewName ) {
		logger.info("Requesting a new view {} from the clientProxy.", viewName);

		DataElementDataView dedv = DataElementStore.getInstance().getDataElementDataView(viewName) ;
		if( dedv==null ) {
			logger.warn( "View {} is not defined.", viewName ) ;			
		} else {
			try {
				ClientDataView newView = new ClientDataView(dedv, clientCommandProcessor) ;

				logger.debug( "Replaying all data elements to {}", viewName ) ;
				newView.sendAll() ;
				logger.info( "Replayed all elements to {}", viewName );

				clientCommandProcessor.initializationComplete(viewName);
				openDataViews.put( viewName, newView ) ;			
				logger.info( "Added new dataView '{}'. {} views now exist.", viewName, openDataViews.size() );
			} catch( ClientDisconnectedException cde ) {
				logger.error( "Failed to open a new view " + viewName , cde ) ;
			}
		}
	}

	/**
	 * When the client shuts down - closes web page, this is called
	 * we shut down everything attached to the same websocket
	 */
	public void close() {		
		logger.info( "Requesting close of entire client - removing all views." ) ;
		for( String viewName : openDataViews.keySet() ) {
			clientCommandProcessor.close( viewName ) ;
		}
		openDataViews.clear();
		logger.info( "All views cleared. ClientProxy is terminated.");
	}

	/**
	 * Debug stuff - can be useful for logging too
	 */
	public String toString() {
		StringBuilder rc = new StringBuilder( "Open views [ " ) ; 	
		for( String openView : openDataViews.keySet() ) {
			rc.append( openView ).append(' ' ).append( ',' ) ;
		}
		rc.setCharAt( rc.length()-1, ']' ) ;
		return rc.toString() ;
	}

}