package com.rc.agg.client;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;
import com.rc.dataview.ClientDataView;
import com.rc.dataview.DataElementDataView;
import com.rc.dataview.DataElementStore;

/**
 * This handles all messages from the client.
 * 
 * @see ClientCommaneProcessor which handles events to the client.
 * 
 * @author richard
 *
 */
public class ClientProxy implements ClientEventProcessor {

	Logger logger = LoggerFactory.getLogger( ClientProxy.class ) ;

	private final Map<String,ClientDataView> openDataViews ;
	private ClientCommandProcessor clientCommandProcessor ;
	
	public ClientProxy( ClientCommandProcessor clientCommandProcessor ) {
		openDataViews = new ConcurrentHashMap<>();
		this.clientCommandProcessor = clientCommandProcessor ;
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
		String rc[] = null ;
		try {
			if( request.command.equals("STOP") ) {		// shutdown the whole client
				// Stoppage - no reply necessary, but just in case prepare one...				
				rc = new String[] { "OK" } ;
			} else if( request.command.equals("START") ) {
				// Something started - assume OK. Any failure will not send a message, the client must timeout				
				rc = new String[] { "OK" } ;
			} else if( request.command.equals("VIEWS") ) {		// list available views
				// Answer with the list of available views that can be shown on the client desktop
				StringBuilder tmp = new StringBuilder() ;
				boolean first = true ;
				Collection<String> vds = DataElementStore.getInstance().getDataViewNames() ;
				rc = new String[ vds.size() ] ;
				int i = 0 ;
				for( String vd : vds ) {
					rc[i++] = vd ;
				}
			} else if( request.command.equals("ATTRIBUTES") ) {
				rc = new String[]{ "BOOK", "CCY", "TRADEID" } ;
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

	@Override
	public void expandCollapseRow(String viewName, String rowKeys[], boolean expanded) {
		ClientDataView dgp = openDataViews.get(viewName) ;
		if( dgp == null ) {
			logger.info( "Ignoring expand/collapse request for unknown view: '{}'.", viewName  ) ;
		} else {
			dgp.expandCollapseRow( DataElement.mergeComponents(rowKeys), expanded) ;			
		}
	}
	@Override
	public void expandCollapseCol(String viewName, String colKeys[], boolean expanded) {
		ClientDataView dgp = openDataViews.get(viewName) ;
		if( dgp == null ) {
			logger.info( "Ignoring expand/collapse request for unknown view: '{}'.", viewName  ) ;
		} else {
			dgp.expandCollapseCol( DataElement.mergeComponents(colKeys), expanded) ;			
		}
	}

	// CLient closed a view
	public void closeView( String viewName ) {
		ClientDataView dgp = openDataViews.remove(viewName) ;
		if( dgp == null ) {
			logger.warn( "Cannot find {} in the openViews.", viewName );
		} else {
			dgp.close();
			logger.info( "Removed '{}' from openViews. {} views remain.", viewName, openDataViews.size() );
		}
	}

	// Client requested complete refresh of a view
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

	public void openView( String viewName, String openColKeys[], String openRowKeys[] ) {
		DataElementDataView dedv = DataElementStore.getInstance().getDataElementDataView(viewName) ;
		if( dedv==null ) {
			logger.warn( "View {} is not defined.", viewName ) ;			
		} else {
			try {
				ClientDataView newView = new ClientDataView(dedv, clientCommandProcessor) ;
				if( openRowKeys != null ) {
					for( String openRowKey : openRowKeys ) {
						newView.expandCollapseRow( openRowKey, true ) ;
					}
				}
				if( openColKeys != null ) {
					for( String openColKey : openColKeys ) {
						newView.expandCollapseCol( openColKey, true ) ;
					}
				}
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

	public void close() {		
		logger.info( "Requesting close of entire client - removing all views." ) ;
		for( String viewName : openDataViews.keySet() ) {
			clientCommandProcessor.close( viewName ) ;
		}
		openDataViews.clear();
		logger.info( "All views cleared. ClientProxy is terminated.");
	}


	public String toString() {
		StringBuilder rc = new StringBuilder( "Open views [ " ) ; 	
		for( String openView : openDataViews.keySet() ) {
			rc.append( openView ).append(' ' ).append( ',' ) ;
		}
		rc.setCharAt( rc.length()-1, ']' ) ;
		return rc.toString() ;
	}

}