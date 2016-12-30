package com.rc.dataview;

import java.text.DecimalFormat;
import java.util.HashSet;
import java.util.Set;

import com.rc.agg.client.ClientCommandProcessor;
import com.rc.agg.client.ClientDisconnectedException;

/**
 * This class represents the client view grid. It accepts dataViewElements element updates
 * and processes them in real-time
 * 
 * @author richard
 *
 */
public class ClientDataView  /*implements Runnable */ {

	private final DataElementDataView dataElementDataView ;	
	private final ClientCommandProcessor clientCommandProcessor ;		// how to pass the new view to the client
	private final Set<String> expandedRows ;
	private final Set<String> expandedCols ;
	//	private Thread messageSender ;
	private volatile boolean clientReadyToReceive ;

	public ClientDataView( 
			DataElementDataView dataElementDataView,
			ClientCommandProcessor clientCommandProcessor ) {

		this.dataElementDataView = dataElementDataView ;
		this.clientCommandProcessor = clientCommandProcessor ;		
		this.expandedRows = new HashSet<>();
		this.expandedCols = new HashSet<>();

		dataElementDataView.addClient( this ); 

		String colKey = "" ;
		boolean first = true ;
		for( String colGroup : dataElementDataView.getColGroups() ) {
			if( first ) { first=false ;}
			else { colKey += '\t' ;}
			colKey += colGroup ;
		}

		String rowKey = "" ;
		first = true ;
		for( String rowGroup : dataElementDataView.getRowGroups() ) {
			if( first ) { first=false ;}
			else { rowKey += '\t' ; }
			rowKey += rowGroup ;
		}
		clientCommandProcessor.defineGrid( getViewName(), colKey, rowKey, dataElementDataView.getDescription() ) ;
		activate(true); 
	}

	//	public void start() {
	//		messageSender = new Thread( this ) ;
	//		messageSender.start();
	//	}

	public void close() {
		this.dataElementDataView.removeClient( this ); 
	}

	public void expandCollapseRow( String rowKey, boolean expanded) {
		if( expanded ) { expandedRows.add( rowKey ) ; } else { expandedRows.remove(rowKey) ; } 
	}
	public void expandCollapseCol( String colKey, boolean expanded) {
		if( expanded ) { expandedCols.add( colKey ) ; } else { expandedCols.remove(colKey) ; } 
	}

	public void activate( boolean active ) {
		// Can now send updates to client
		clientReadyToReceive = active ;
		//		System.out.println( "Client activated - do something here ........") ;
	}


	public void unusedElement( String elementKey, DataViewElement dve ) {
		int ix = elementKey.indexOf('\f' ) ;
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}


	public void updatedElement( String elementKey, DataViewElement dve ) {
		int ix = elementKey.indexOf('\f' ) ;
		String colKey = elementKey.substring(0,ix) ;
		String rowKey = elementKey.substring(ix+1) ;
		DecimalFormat numberFormatter = new DecimalFormat( "#,##0;(#,##0)") ;

		try {
			boolean rowExpanded = parentRowKeysExpanded(rowKey) ;
			boolean colExpanded = parentColKeysExpanded(colKey) ;
			
			if( rowExpanded && colExpanded ) {
				clientCommandProcessor.updateCell( 
						getViewName(), 
						colKey, 
						rowKey,
						numberFormatter.format(dve.getValue())  
						) ;
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	/**
	 *  Look at all the saved elements and send any that have changed.
	 */

	public synchronized void sendAll() {
		dataElementDataView.sendAll( this );
	}


	protected boolean parentKeysExpanded( String colKey, String rowKey ) {
		return parentRowKeysExpanded(rowKey) & parentColKeysExpanded(colKey) ;
	}

	protected boolean parentRowKeysExpanded( String rowKey ) {
		boolean rc = true ;				
		String parentRowKey = rowKey ; 
		for( int i = rowKey.lastIndexOf('\t') ; i>0 ; i=parentRowKey.lastIndexOf('\t') ) {
			parentRowKey = parentRowKey.substring(0,i) ;
			rc &= expandedRows.contains( parentRowKey ) ;
		}
		return rc ;
	}

	protected boolean parentColKeysExpanded( String colKey ) {
		boolean rc = true ;				
		String parentColKey = colKey ; 
		for( int i = colKey.lastIndexOf('\t') ; i>0 ; i=parentColKey.lastIndexOf('\t') ) {
			parentColKey = parentColKey.substring(0,i) ;
			rc &= expandedCols.contains( parentColKey ) ;
		}
		return rc ;
	}


	public String toString() {
		return getViewName() + " Expanded Cols :" + expandedCols.size() + " Expanded Rows :" + expandedRows.size() ; 
	}


	//	@Override
	//	public void run() {
	//		try {
	//			while( messageSender!=null && !messageSender.isInterrupted() ) {
	//				Thread.sleep( 500 );
	//				if( clientReadyToReceive ) {
	//					sendUpdates();
	//				}
	//			} 
	//		} catch( InterruptedException ignore ) {
	//
	//		} catch( Throwable t ) {
	//			t.printStackTrace();  // should never happen
	//		}
	//	}
	//
	protected String getViewName() {
		return dataElementDataView.getViewName() ;
	}
}


