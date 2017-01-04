package com.rc.dataview;

import java.text.DecimalFormat;
import java.util.Set;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.client.ClientCommandProcessor;
import com.rc.agg.client.ClientCommandProcessorImpl;
import com.rc.agg.client.ClientDisconnectedException;
import com.rc.datamodel.DataElement;

/**
 * This class represents the client view grid. It accepts dataViewElements element updates
 * and processes them in real-time
 * 
 * @author richard
 *
 */
public class ClientDataView  {
	Logger logger = LoggerFactory.getLogger( ClientDataView.class ) ;

	private final DataElementDataView dataElementDataView ;	
	private final ClientCommandProcessor clientCommandProcessor ;		// how to pass the new view to the client
	private final Set<String> expandedRows ;
	private final Set<String> expandedCols ;
	private boolean closed ;

	public ClientDataView( 
			DataElementDataView dataElementDataView,
			ClientCommandProcessor clientCommandProcessor ) throws ClientDisconnectedException {

		this.dataElementDataView = dataElementDataView ;
		this.clientCommandProcessor = clientCommandProcessor ;		
		this.expandedRows = new ConcurrentHashSet<>() ;
		this.expandedCols = new ConcurrentHashSet<>() ;
		this.closed = false ;
		
		dataElementDataView.addClient( this ); 

		clientCommandProcessor.defineGrid( 
				getViewName(), 
				DataElement.mergeComponents( dataElementDataView.getColGroups() ) , 
				DataElement.mergeComponents( dataElementDataView.getRowGroups() ) , 
				dataElementDataView.getDescription() ) ;
	}

	
	public boolean isClosed() {
		return closed;
	}

	public void close() {
		logger.info( "Marking ClientDataView {} as closed.", getViewName() ) ;
		clientCommandProcessor.close( getViewName() ) ;
		closed = true ; 
	}

	public void reset() {
		logger.info( "Informing ClientDataView {} that a reset is required.", getViewName() ) ;
		try {
			clientCommandProcessor.reset( getViewName() ) ;
		} catch (ClientDisconnectedException e) {
			logger.warn( "Remote client for {} disconnected during reset.", getViewName() ) ;
			close(); 
		}
	}

	public void expandCollapseRow( String rowKey, boolean expanded) {
		if( expanded ) { expandedRows.add( rowKey ) ; } else { expandedRows.remove(rowKey) ; } 
	}
	public void expandCollapseCol( String colKey, boolean expanded) {
		if( expanded ) { expandedCols.add( colKey ) ; } else { expandedCols.remove(colKey) ; } 
	}


	public void unusedElement( String elementKey, DataViewElement dve ) {
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


	public void updatedElement( String elementKey, DataViewElement dve ) {
		if( isClosed() ) return ;
		
		int ix = elementKey.indexOf( DataElement.ROW_COL_SEPARATION_CHAR ) ;
		String colKey = elementKey.substring(0,ix) ;
		String rowKey = elementKey.substring(ix+1) ;

		try {
			boolean rowExpanded = parentRowKeysExpanded(rowKey) ;
			boolean colExpanded = parentColKeysExpanded(colKey) ;
			
			if( rowExpanded && colExpanded ) {
				DecimalFormat numberFormatter = new DecimalFormat( "#,##0;(#,##0)") ;
				if( dve != null ) {   // it may be null at first view open
					clientCommandProcessor.updateCell( 
						getViewName(), 
						colKey, 
						rowKey,
						numberFormatter.format(dve.getValue())  
						) ;
				}
			} else {
				if( !rowExpanded ) {
					clientCommandProcessor.deleteRow( 
							getViewName(), 
							rowKey
							) ;
				} 
				if( !colExpanded ) {
					clientCommandProcessor.deleteCol( 
							getViewName(), 
							colKey 
							) ;
				}
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


	protected boolean parentKeysExpanded( String colKey, String rowKey ) {
		// isClosed - if the view is closed don't attempt to send anything
		// otherwise make sure all the parent keys in each row & col expanded rows/cols
		// are marked as expanded.
		return parentRowKeysExpanded(rowKey) & parentColKeysExpanded(colKey) ;
	}

	/**
	 * Verify that ALL parent keys of the given key are currently expanded.
	 * Just because a grandchild is 'open' it may not be visible
	 * if its parent is 'closed'
	 * 
	 * @param rowKey
	 * @return
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

	protected boolean parentColKeysExpanded( String colKey ) {
		boolean rc = !isClosed()  ;				
		String parentColKey = colKey ; 
		for( int i = colKey.lastIndexOf(DataElement.SEPARATION_CHAR) ; i>0 ; i=parentColKey.lastIndexOf(DataElement.SEPARATION_CHAR) ) {
			parentColKey = parentColKey.substring(0,i) ;
			rc &= expandedCols.contains( parentColKey ) ;
		}
		return rc ;
	}


	public String toString() {
		return getViewName() + " Expanded Cols :" + expandedCols.size() + " Expanded Rows :" + expandedRows.size() + " " + (isClosed()?"Closed" : "Active") ; 
	}


	protected String getViewName() {
		return dataElementDataView.getViewName() ;
	}
}

