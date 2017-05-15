package com.rc.dataview;

import java.text.DecimalFormat;
import java.util.Set;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.client.ClientCommandProcessor;
import com.rc.agg.client.ClientDisconnectedException;
import com.rc.datamodel.DataElement;

/**
 * This class represents the client view. It accepts dataViewElements element updates
 * and processes them in real-time
 * 
 * @author richard
 *
 */
public class ClientDataView  {
	final static Logger logger = LoggerFactory.getLogger( ClientDataView.class ) ;

	private final DataElementDataView dataElementDataView ;	
	private final ClientCommandProcessor clientCommandProcessor ;		// how to pass the new view to the client
	private final Set<String> expandedRows ;
	private final Set<String> expandedCols ;
	private boolean closed ;

	/**
	 * Create one of these, called when a client connects.
	 * It keeps a reference to the dataElementDataView and
	 * the Command processor (used to send messages to the client)
	 * 
	 * @param dataElementDataView
	 * @param clientCommandProcessor
	 * @throws ClientDisconnectedException
	 */
	public ClientDataView( 
			DataElementDataView dataElementDataView,
			ClientCommandProcessor clientCommandProcessor ) throws ClientDisconnectedException {

		this.dataElementDataView = dataElementDataView ;
		this.clientCommandProcessor = clientCommandProcessor ;		
		this.expandedRows = new ConcurrentHashSet<>() ;
		this.expandedCols = new ConcurrentHashSet<>() ;
		this.closed = false ;
		
		dataElementDataView.addClient( this ); 

		clientCommandProcessor.defineView( 
				getViewName(), 
				DataElement.mergeComponents( dataElementDataView.getColGroups() ) , 
				DataElement.mergeComponents( dataElementDataView.getRowGroups() ) , 
				dataElementDataView.getDescription() ) ;
	}

	/**
	 * When the view is closed (client disconnects) this is set
	 * so that no further messaging is done during shutdown processing
	 * The instance is NOT expected to live beyond closure.
	 * @return whether the view is set to close soon
	 */
	public boolean isClosed() {
		return closed;
	}

	/**
	 * Call this to disconnect a client (on request or shutdown of client messaging)
	 * 
	 */
	public void close() {
		logger.info( "Marking ClientDataView {} as closed.", getViewName() ) ;
		clientCommandProcessor.close( getViewName() ) ;
		closed = true ; 
	}

	/**
	 * If the client wishes this will resnd a copy of the entire view
	 * as updates. 
	 * It should be called whenever the client believes it has lost sync
	 * (or perhaps a client that ignores updates)
	 */
	public void reset() {
		logger.info( "Informing ClientDataView {} that a reset is required.", getViewName() ) ;
		try {
			clientCommandProcessor.reset( getViewName() ) ;
		} catch (ClientDisconnectedException e) {
			logger.warn( "Remote client for {} disconnected during reset.", getViewName() ) ;
			close(); 
		}
	}
	/**
	 * This is called when a client expands or closes a row. It maintains a set of
	 * currently expanded keys. Rows that are collapsed will not receive updated messages
	 * @param rowKey
	 * @param expanded
	 */
	public void expandCollapseRow( String rowKey, boolean expanded) {
		if( expanded ) { expandedRows.add( rowKey ) ; } else { expandedRows.remove(rowKey) ; } 
	}
	
	/**
	 * This is called when a client expands or closes a column. It maintains a set of
	 * currently expanded keys. Columns that are collapsed will not receive updated messages
	 * @param colKey
	 * @param expanded
	 */
	public void expandCollapseCol( String colKey, boolean expanded) {
		if( expanded ) { expandedCols.add( colKey ) ; } else { expandedCols.remove(colKey) ; } 
	}

	/**
	 * Mark an element as unused ( just beenb hidded for example by closing a row or column )
	 * An usused element is deleted from the client view.
	 * @param elementKey
	 */
	public void unusedElement( String elementKey ) {
		int ix = elementKey.indexOf( DataElement.ROW_COL_SEPARATION_CHAR ) ;
		String colKey = elementKey.substring(0,ix) ;
		String rowKey = elementKey.substring(ix+1) ;

		if( parentKeysExpanded(colKey, rowKey) ) {
			try {
				clientCommandProcessor.deleteCell( 
						getViewName(), 
						colKey, 
						rowKey 
						) ;
			} catch (ClientDisconnectedException e) {
				logger.warn( "Remote client for {} disconnected during cell delete.", getViewName() ) ;
				close(); 
			}
		}
	}


	/**
	 * When an element is marked as updated it will be sent to the client
	 * in an update message
	 * 
	 * @param elementKey the full key
	 * @param value the new data value
	 */
	public void updatedElement( String elementKey, float value ) {
		if( isClosed() ) return ;
		
		int ix = elementKey.indexOf( DataElement.ROW_COL_SEPARATION_CHAR ) ;
		String colKey = elementKey.substring(0,ix) ;
		String rowKey = elementKey.substring(ix+1) ;

		try {
			boolean rowExpanded = parentRowKeysExpanded(rowKey) ;
			boolean colExpanded = parentColKeysExpanded(colKey) ;
			
			if( rowExpanded && colExpanded ) {
				DecimalFormat numberFormatter = new DecimalFormat( "#,##0;(#,##0)") ;
				clientCommandProcessor.updateCell( 
						getViewName(), 
						colKey, 
						rowKey,
						numberFormatter.format(value)  
						) ;
			}
		} catch (ClientDisconnectedException e) {
			logger.warn( "Remote client for {} disconnected during cell update.", getViewName() ) ;
			close(); 
		}
	}


	/**
	 *  Look at all the saved elements and send any that have changed.
	 */
	public synchronized void sendAll() {
		dataElementDataView.sendAll( this );
	}


	/**
	 * Verify that ALL parent keys of the given key are currently expanded.
	 * Just because a grandchild is 'open' it may not be visible
	 * if its parent is 'closed'. 
	 * 
	 * @param colKey the child column key to search up the path from
	 * @param rowKey the child row key to search up the path from
	 * @return whether the parent rows keys are expanded
	 */
	protected boolean parentKeysExpanded( String colKey, String rowKey ) {
		// isClosed - if the view is closed don't attempt to send anything
		// otherwise make sure all the parent keys in each row & col expanded rows/cols
		// are marked as expanded.
		return parentRowKeysExpanded(rowKey) && parentColKeysExpanded(colKey) ;
	}

	/**
	 * Verify that ALL parent keys of the given key are currently expanded.
	 * Just because a grandchild is 'open' it may not be visible
	 * if its parent is 'closed'. 
	 * 
	 * @param rowKey the child row key to search up the path from
	 * @return whether the parent rows keys are expanded
	 */
	protected boolean parentRowKeysExpanded( String rowKey ) {
		boolean rc = !isClosed() ;				
		String parentRowKey = rowKey ; 
		for( int i = rowKey.lastIndexOf( DataElement.SEPARATION_CHAR) ; i>0 ; i=parentRowKey.lastIndexOf(DataElement.SEPARATION_CHAR) ) {
			parentRowKey = parentRowKey.substring(0,i) ;
			rc &= expandedRows.contains( parentRowKey ) ;
		}
		return rc ;
	}

	/**
	 * Verify that ALL parent keys of the given key are currently expanded.
	 * Just because a grandchild is 'open' it may not be visible
	 * if its parent is 'closed'. 
	 * 
	 * @param colKey the child column key to search up the path from
	 * @return whether the parent rows keys are expanded
	 */
	protected boolean parentColKeysExpanded( String colKey ) {
		boolean rc = !isClosed()  ;				
		String parentColKey = colKey ; 
		for( int i = colKey.lastIndexOf(DataElement.SEPARATION_CHAR) ; i>0 ; i=parentColKey.lastIndexOf(DataElement.SEPARATION_CHAR) ) {
			parentColKey = parentColKey.substring(0,i) ;
			rc &= expandedCols.contains( parentColKey ) ;
		}
		return rc ;
	}

	/**
	 * Used for debug etc.
	 */
	public String toString() {
		return getViewName() + " Expanded Cols :" + expandedCols.size() + " Expanded Rows :" + expandedRows.size() + " " + (isClosed()?"Closed" : "Active") ; 
	}

	/**
	 * Return the name of this view - used in keying. This is NOT the description
	 * It copies the name from the dataElementDataView instance.
	 * 
	 * @return the name of the view
	 */
	protected String getViewName() {
		return dataElementDataView.getViewName() ;
	}
}

