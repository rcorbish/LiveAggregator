package com.rc.agg.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.WebSocketServer;
import com.rc.dataview.ClientDataView;
import com.rc.dataview.DataElementDataView;

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

	private final Map<String,ClientDataView> openDataGrids ;
	private ClientCommandProcessor clientCommandProcessor ;
	private ClientManager clientManager ;

	public ClientProxy( ClientManager clientManager, ClientCommandProcessor clientCommandProcessor ) {
		openDataGrids = new ConcurrentHashMap<>();
		this.clientCommandProcessor = clientCommandProcessor ;
		this.clientManager = clientManager ;
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
	public CharSequence respond( String request ) {
		CharSequence rc = null ;
		try {
			if( "STOP".equals(request) ) {		// shutdown the whole client
				// Stoppage - no reply necessary, but just in case prepare one...				
				rc = "OK" ;
			} else if( "START".equals(request) ) {
				// Something started - assume OK. Any failure will not send a message, the client must timeout				
				rc = "OK" ;				
			} else if( "VIEWS".equals(request) ) {		// list available views
				// Answer with the list of available views that can be shown on the client desktop
				StringBuilder tmp = new StringBuilder() ;
				boolean first = true ;
				for( String viewName : clientManager.getDataViewNames() ) {
					if( first ) first = false ; else tmp.append('\t') ;
					tmp.append( viewName ) ;
				}
				rc = tmp;
			} else if( "ATTRIBUTES".equals(request) ) {
				// List available attribute for filtering/grouping etc. 				
				rc = "BOOK\tCCY\tTRADEID" ;
			}
		} catch( Exception ex ) {
			logger.error( "Error responding to request.", ex ) ;
		}
		return rc ;
	}

	@Override
	public void gridReady(String gridName) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			logger.info( "Ignoring grid ready info for unknown grid: '{}'.", gridName  ) ;
		}
	}

	@Override
	public void expandCollapseRow(String gridName, String rowKey,
			boolean expanded) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			logger.info( "Ignoring expand/collapse request for unknown grid: '{}'.", gridName  ) ;
		} else {
			dgp.expandCollapseRow( rowKey, expanded) ;			
		}
	}
	@Override
	public void expandCollapseCol(String gridName, String colKey,
			boolean expanded) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			logger.info( "Ignoring expand/collapse request for unknown grid: '{}'.", gridName  ) ;
		} else {
			dgp.expandCollapseCol( colKey, expanded) ;			
		}
	}

	// CLient closed a grid
	public void closeGrid( String gridName ) {
		ClientDataView dgp = openDataGrids.remove(gridName) ;
		if( dgp == null ) {
			logger.warn( "Cannot find {} in the openGrids.", gridName );
		} else {
			dgp.close();
			logger.info( "Removed '{}' from openGrids. {} grids remain.", gridName, openDataGrids.size() );
		}
	}

	// Client requested complete refresh of a grid
	public void resetGrid( String gridName ) {
		logger.info( "Resetting grid {}", gridName ) ;
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			logger.warn( "Cannot find {} in the openGrids.", gridName );
		} else {
			dgp.sendAll() ;
			clientCommandProcessor.initializationComplete(gridName);
			logger.info( "Reset grid {} completed.", gridName ) ;
		}
	}

	public void openGrid( String gridName, List<String> openColKeys, List<String> openRowKeys ) {
		DataElementDataView dedv = clientManager.getDataElementDataView(gridName) ;
		if( dedv==null ) {
			logger.warn( "Grid {} is not defined.", gridName ) ;			
		} else {
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
			long start = System.currentTimeMillis() ;
			logger.debug( "Replaying all data elements to {}", gridName ) ;
			newView.sendAll() ;
			logger.info( "Replayed all elements to {} in {} mS", gridName, (System.currentTimeMillis()-start) );
			
			clientCommandProcessor.initializationComplete(gridName);
			openDataGrids.put( gridName, newView ) ;			
			logger.debug( "Added new dataGrid '{}'. {} grids now exist.", gridName, openDataGrids.size() );
		}
	}

	public void close() {		
		logger.info( "Requesting close of entire client - removing all grids." ) ;
		for( String gridName : openDataGrids.keySet() ) {
			clientCommandProcessor.closeClient( gridName );
		}
		openDataGrids.clear();
		logger.info( "All grids cleared.");
	}


	/*
	 * Section for the monitor ....
	 */

	public Map<String, ClientDataView> getOpenDataGrids() {
		return openDataGrids;
	}

	public String toString() {
		return "Client " + clientCommandProcessor ;
	}

}