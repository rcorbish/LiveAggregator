package com.rc.dataview;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.DataElementProcessor;
import com.rc.datamodel.DataElement;

/**
 * This class represents the view grid. It maintains a view of
 * all possible (maximally expanded) data elements. Each connected
 * client view will be used to 'filter' down to the subset of
 * expanded cells for a partoicular client.
 * 
 * It accepts dataViewElements element updates and processes them in 
 * real-time to keep a live view of the model subset. This is the 
 * aggregation step.
 * 
 * @author richard
 *
 */
public class DataElementDataView  implements DataElementProcessor, Runnable {

	Logger logger = LoggerFactory.getLogger( DataElementDataView.class ) ;

	// How often to send an update to the client (millis)
	public static int CLIENT_UPDATE_INTERVAL = 200 ;	
	
	private final Map<String,String> filters ; 		// what key = value is being filtered
	private final String colGroups[] ; 				// what is getting grouped
	private final String rowGroups[] ; 				// what is getting grouped

	private final Map<String,DataViewElement>   dataViewElements ;	// raw dataViewElements that needs to be updated is stored here
	private final String viewName ;
	private final String description ;
	
	private volatile boolean serverBatchComplete ;
	private List<ClientDataView> clientViews ;	// which clients need to be told about updates?
	private Thread messageSender ;


	public DataElementDataView( ViewDefinition viewDefinition ) {
		
		this.serverBatchComplete = false ;
		this.clientViews = new ArrayList<>() ;
		dataViewElements = new ConcurrentHashMap<>() ;
		
		this.viewName = viewDefinition.getName() ;
		this.description = viewDefinition.getDescription() ;
		this.filters = viewDefinition.getFilters();

		if( viewDefinition.getColGroups().length == 0 ) {
			colGroups = new String[] { "--" } ; 
		} else {
			this.colGroups = viewDefinition.getColGroups() ;
		}
		if( viewDefinition.getRowGroups().length == 0 ) {
			rowGroups = new String[] { "--" } ; 
		} else {
			this.rowGroups = viewDefinition.getRowGroups() ;
		}
	}
	
	
	public void start() {
		messageSender = new Thread( this ) ;
		messageSender.start();
	}

	public void stop() {
		if( messageSender != null ) {
			messageSender.interrupt();
			// now send a stop to each client, because of messaging we must
			// copy the array - as other messages can change this array during iteration
			// esp. the stop response from the client.
			List<ClientDataView> tmp = new ArrayList<>( clientViews.size() ) ;
			for( ClientDataView cdv : clientViews ) {
				tmp.add( cdv ) ;
			}			
			for( ClientDataView cdv : tmp ) {
				cdv.close(); 
			}
			// After closing - let's remove all the views from  this DataView
			clientViews.removeAll( tmp ) ;
		}
	}
	
	/**
	 * Call this to reset - ie. clear the entire view
	 */
	public synchronized void resetAndStop() {
		for( ClientDataView cdv : clientViews ) {
			cdv.reset(); 
		}
		stop() ;
	}
	
	public String getDescription() {
		return description;
	}


	/**
	 * Does a data element match the currently set filter?
	 * @param element
	 * @return
	 */
	private boolean matchesPerimeterElements(int index, DataElement element) {
		boolean rc = true ;
		if( filters != null && !filters.isEmpty() ) {
			for( String k : filters.keySet() ) {
				String mustMatchSomethingInHere = filters.get(k) ;				
				String []mustMatchOneOfThese = DataElement.splitComponents(mustMatchSomethingInHere) ;
				boolean matchedOneOfthese = false ;
				for( String couldMatchThis : mustMatchOneOfThese ) {
					String att = element.getAttribute( index, k ) ;
					if( att != null ) {
						matchedOneOfthese |= att.equals( couldMatchThis ) ;
						if( matchedOneOfthese ) break ;
					}
				}
				rc &= matchedOneOfthese ;
				if( !rc ) break ;
			}
		}
		return rc ;
	}


	private boolean failedCoreMatch(DataElement element) {
		boolean rc = false ;
		if( filters != null && !filters.isEmpty() ) {
			for( String k : filters.keySet() ) {
				String mustMatchSomethingInHere = filters.get(k) ;
				String []mustMatchOneOfThese = DataElement.splitComponents( mustMatchSomethingInHere ) ;
				boolean failedToMatcheOneOfThese = false;
				for( String couldMatchThis : mustMatchOneOfThese ) {
					String att = element.getCoreAttribute( k ) ;
					if( att != null ) {
						failedToMatcheOneOfThese |= !att.equals( couldMatchThis ) ;
						if( failedToMatcheOneOfThese ) break ;
					}
				}
				rc |= failedToMatcheOneOfThese ;
				if( rc ) break ;
			}
		}
		return rc ;
	}


	/**
	 *  Look at all the saved elements and send any that have changed.
	 *  @TODO Since data can change - in a diff thread - this needs to
	 *  be cleaned up to separate the row keys from the underlying data
	 */
	private synchronized void sendUpdates() {
		if( serverBatchComplete ) {
			List<String>	removedKeys = new ArrayList<>() ;
			for( String elementKey : dataViewElements.keySet() ) {
				DataViewElement dve = dataViewElements.get( elementKey ) ;
				if( dve.unused ) {					
					for( ClientDataView cdv : clientViews ) {
						cdv.unusedElement( elementKey, dve ) ;
					}
					removedKeys.add( elementKey ) ;						
				} else if( dve.updated ) {
					for( ClientDataView cdv : clientViews ) {
						cdv.updatedElement( elementKey, dve ) ;
					}
					dve.clear();
				}
			}
			for( String removedKey : removedKeys ) {
				dataViewElements.remove(removedKey) ;
			}
		}
	}


	/**
	 *  Look at all the saved elements and send any that have changed.
	 *  @TODO Since data can change - in a diff thread - this needs to
	 *  be cleaned up to separate the row keys from the underlying data
	 */
	public void sendAll( ClientDataView cdv ) {
		for( String elementKey : dataViewElements.keySet() ) {
			DataViewElement dve = dataViewElements.get( elementKey ) ;
			cdv.updatedElement( elementKey, dve ) ;
		}
	}


	/**
	 * Adds an element to the data view. This needs to figure out all the
	 * combinations of keys and add the value to the pre-calculated pieces.
	 * 
	 * This method probably consumes 90% of the CPU capacity - be careful editing
	 * 
	 */
	public void process( DataElement dataElement ) {

		if( !failedCoreMatch( dataElement ) ) {
			StringBuilder colKeyPiece = new StringBuilder() ;
			StringBuilder elementKey = new StringBuilder() ;

			for( int i=0 ; i<dataElement.size() ; i++ ) {
				if( matchesPerimeterElements( i, dataElement ) ) {
					colKeyPiece.setLength(0);

					for( String colGroup : colGroups ) {
						colKeyPiece.append( dataElement.getAttribute(i, colGroup ) ) ;
						elementKey.setLength(0);
						elementKey.append( colKeyPiece ).append( DataElement.ROW_COL_SEPARATION_CHAR ) ;
						for( String rowGroup : rowGroups ) {
							elementKey.append( dataElement.getAttribute(i, rowGroup ) ) ;
							String key = elementKey.toString() ;
							DataViewElement dve = dataViewElements.get( key ) ;
							if( dve == null ) {
								// Allow concurrent elem creates
								DataViewElement newDve = new DataViewElement() ;
								dve = dataViewElements.putIfAbsent( key, newDve ) ;
								if( dve==null ) {
									dve = newDve ;
								}
							}
							dve.add( dataElement.getValue(i) )  ;
							elementKey.append( DataElement.SEPARATION_CHAR ) ;
						}					
						colKeyPiece.append( DataElement.SEPARATION_CHAR ) ;
					}
				}
			}
		}
	}



	public void startBatch() {		
		serverBatchComplete = false ;		
		for( DataViewElement dve : dataViewElements.values() ) {
			dve.markUnused();
		}
	}

	public void endBatch() {
		serverBatchComplete = true ;
		sendUpdates();
	}

	public String[] getColGroups() {
		return colGroups;
	}


	public String[] getRowGroups() {
		return rowGroups;
	}


	public String getViewName() {
		return viewName;
	}


	public synchronized void removeClient( ClientDataView client ) {
		this.clientViews.remove( client ) ;
	}

	public synchronized void addClient( ClientDataView client ) {
		this.clientViews.add( client ) ;
	}

	public String toString() {
		return viewName + 
				" Server Batch Complete:" + serverBatchComplete  + 
				" View Size: " + dataViewElements.size() ; 
	}

	@Override
	public void run() {
		Thread.currentThread().setName( getViewName() + " Update Sender" );
		while( messageSender!=null && !messageSender.isInterrupted() ) {
			try {
				Thread.sleep( CLIENT_UPDATE_INTERVAL );
				if( serverBatchComplete ) {
					sendUpdates();
				}
								
				ListIterator<ClientDataView> iter = clientViews.listIterator();
				while(iter.hasNext()){
				    if(iter.next().isClosed() ){
				        iter.remove();
				        logger.info( "Removing closed ClientDataView." ) ;
				    }
				}

			} catch( InterruptedException ignore ) {
				break ;
			} catch( Throwable t ) {
				logger.error( "Error sending updates: ", t ) ;
			}
		}
		logger.info( "Message sender for {} is shutdown.", getViewName() ) ;
		messageSender = null ;
	} 
		
}



