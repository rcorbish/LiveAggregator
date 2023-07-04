package com.rc.dataview;

import java.text.DecimalFormat;
import java.util.Arrays;

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
	private boolean closed ;

	private int rate ;

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
		this.closed = false ;
		
		dataElementDataView.addClient( this ); 
 
		String[] columns = dataElementDataView.getColGroups() ;
		for( int i=0 ; i<columns.length ; i++ ) {
			if( dataElementDataView.isAttributeHidden( columns[i] ) ) {
				columns  = Arrays.copyOf( columns, i ) ;
				break ;
			}
		}
		String[] rows = dataElementDataView.getRowGroups() ;
		for( int i=0 ; i<rows.length ; i++ ) {
			if( dataElementDataView.isAttributeHidden( rows[i] ) ) {
				rows  = Arrays.copyOf( rows, i ) ;
				break ;
			}
		}
		clientCommandProcessor.defineView( 
				getViewName(), 
				DataElement.mergeComponents( columns ) , 
				DataElement.mergeComponents( rows ) , 
				dataElementDataView.getDescription() ) ;
	}

	/**
	 * When the view is closed (client disconnects) this is set
	 * so that no further messaging is done during shutdown processing
	 * The instance is NOT expected to live beyond closure.
	 * @return whether the view is marked for closure
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
	 * If the client wishes this will resend a copy of the entire view
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
	 * Mark an element as unused ( just been hidden for example by closing a row or column )
	 * An unused element is deleted from the client view.
	 * @param elementKey
	 */
	public void unusedElement( String elementKey ) {
		int ix = elementKey.indexOf( DataElement.ROW_COL_SEPARATION_CHAR ) ;
		String colKey = elementKey.substring(0,ix) ;
		String rowKey = elementKey.substring(ix+1) ;
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


	/**
	 * When an element is marked as updated it will be sent to the client
	 * in an update message
	 * 
	 * @param elementKey the full key
	 * @param value the new data value
	 */
	public void updatedElement( String elementKey, double value ) {
		if( isClosed() ) return ;
		
		int ix = elementKey.indexOf( DataElement.ROW_COL_SEPARATION_CHAR ) ;
		String colKey = elementKey.substring(0,ix) ;
		String rowKey = elementKey.substring(ix+1) ;

		try {
			DecimalFormat numberFormatter = new DecimalFormat( "#,##0;(#,##0)") ;
			clientCommandProcessor.updateCell( 
					getViewName(), 
					colKey, 
					rowKey,
					numberFormatter.format(value)  
					) ;
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
	 * Used for debug etc.
	 */
	public String toString() {
		return getViewName() + " " + (isClosed()?"Closed" : "Active") ; 
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

