package com.rc.agg.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.rc.dataview.ClientDataView;
import com.rc.dataview.DataElementDataView;

public class ClientProxy implements ClientEventProcessor {

	private final Map<String,ClientDataView> openDataGrids ;
	private ClientCommandProcessor clientCommandProcessor ;
	private ClientManager clientManager ;
	private Thread heartbeater ;

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
			System.out.println( "Error responding to request: " + ex.getMessage() ) ;
		}
		return rc ;
	}

	@Override
	public void gridReady(String gridName) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			System.out.println( "Ignoring expand/collapse request for unknown grid: '" + gridName + "'." ) ;
		} else {
			dgp.activate(true) ;
		}
	}

	@Override
	public void expandCollapseRow(String gridName, String rowKey,
			boolean expanded) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			System.out.println( "Ignoring expand/collapse request for unknown grid: '" + gridName + "'." ) ;
		} else {
			dgp.expandCollapseRow( rowKey, expanded) ;			
		}
	}
	@Override
	public void expandCollapseCol(String gridName, String colKey,
			boolean expanded) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			System.out.println( "Ignoring expand/collapse request for unknown grid: '" + gridName + "'." ) ;
		} else {
			dgp.expandCollapseCol( colKey, expanded) ;			
		}
	}

	// CLient closed a grid
	public void closeGrid( String gridName ) {
		ClientDataView dgp = openDataGrids.remove(gridName) ;
		if( dgp == null ) {
			System.out.println( "Cannot find " + gridName + " in the openGrids." );
		} else {
			dgp.close();
			System.out.println( "Removed '" + gridName + "' from openGrids. " + openDataGrids.size() + " grids remain." );
		}
	}

	// Client requested complete refresh of a grid
	public void resetGrid( String gridName ) {
		ClientDataView dgp = openDataGrids.get(gridName) ;
		if( dgp == null ) {
			System.out.println( "Cannot find " + gridName + " in the openGrids." );
		} else {
			dgp.activate(true);
			dgp.sendAll() ;
			clientCommandProcessor.initializationComplete(gridName);
		}
	}

	public void openGrid( String gridName, List<String> openColKeys, List<String> openRowKeys ) {
		DataElementDataView dedv = clientManager.getDataElementDataView(gridName) ;
		if( dedv==null ) {
			System.out.println( "Grid " + gridName + " is not defined." ) ;			
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
			long start = System.nanoTime() ;
			System.out.println( "Replaying all data elements to " + gridName );
			newView.sendAll() ;
			System.out.println( "Replayed all elements to " + gridName +" in " + (System.nanoTime()-start) + "nS." );
			
			clientCommandProcessor.initializationComplete(gridName);
			openDataGrids.put( gridName, newView ) ;			
			System.out.println( "Added new dataGrid '" + gridName + "'. " + openDataGrids.size() + " grids exist." );
//			newView.start();
		}
	}

	public void close() {
		if( heartbeater != null ) {
			try {
				heartbeater.interrupt() ;	// stop heartbeats on a useless channel
				heartbeater = null ;
			} catch( Throwable ignoreError ) {
				// could have a (rare) NullPtr if the thread shuts itself down at the same time
			}
		}
		openDataGrids.clear();
		clientCommandProcessor.closeClient();
	}


	public void start() {
		heartbeater = new Thread( new Runnable() {
			@Override
			public void run() {
				Thread.currentThread().setName( "Heartbeat" ) ;
				try {	
					while( !Thread.currentThread().isInterrupted() ) {
						Thread.sleep( 3000 ) ;
						if( !clientCommandProcessor.heartbeat() ) {
							close() ;
						}
					}
				} catch( InterruptedException ignore ) {
					System.out.println( "Heartbeater interrupted - shutting down now." ) ;
				} finally {
					heartbeater = null ;
				}
			}			
		} ) ;
		heartbeater.start();
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