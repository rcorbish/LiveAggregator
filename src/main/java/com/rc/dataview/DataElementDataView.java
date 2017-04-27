package com.rc.dataview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.DataElementProcessor;
import com.rc.datamodel.DataElement;

/**
 * This class represents the view. It maintains a view of
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
	
	private final Map<String,String[]> filters ; 	// what key = value is being filtered
	private final Map<String,Map<String,String[]>> rowFilters ; 	// what key = value is being filtered for each row key
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

		//----------------------
		// F I L T E R S
		//
		// if a filter is defined make sure any elements in the view
		// match the filter
		this.filters = new HashMap<>() ;		
		
		Map<String,String> rawFilters = viewDefinition.getFilters();
		for( String k : rawFilters.keySet() ) {
			this.filters.put( k, DataElement.splitComponents(rawFilters.get(k)) )  ;
		}
		
		//----------------------
		// R O W   F I L T E R S
		//
		// Match the row filters - use sparingly - not very efficient
		Map<String,Map<String,String[]>> copyRowFilters = new HashMap<>() ;
		
		Map<String,Map<String,String>> rawRowFilters = viewDefinition.getRowFilters();
		for( String k : rawRowFilters.keySet() ) {			
			Map<String,String> rawRowFilter = rawRowFilters.get( k ) ;
			Map<String,String[]> tmp = new HashMap<>() ;
			copyRowFilters.put( k, tmp ) ;
			
			for( String k2 : rawRowFilter.keySet() ) {
				tmp.put( k2, DataElement.splitComponents(rawRowFilter.get(k2)) )  ;
			}
		}
		// set it to null if the filter is empty
		this.rowFilters = copyRowFilters.isEmpty() ? null : copyRowFilters ; 

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
				String mustMatchOneOfThese[] = filters.get(k) ;
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
				String mustMatchOneOfThese[] = filters.get(k) ;
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
			// remember the column keys, we need to have a cartesian
			// of rpw key & column key combinations. 
			StringBuilder colKeyPiece = new StringBuilder( 256 ) ;
			// The cumulative cartesian key for this element
			// need that to keep track of totals
			StringBuilder elementKey = new StringBuilder( 256 ) ;

			// for each sub element
			for( int i=0 ; i<dataElement.size() ; i++ ) {
				//check first part of filter
				if( matchesPerimeterElements( i, dataElement ) ) {
					colKeyPiece.setLength(0);
					// for each column key piece
					for( String colGroup : colGroups ) {
						// add the next piece to the cumulative column key
						colKeyPiece.append( dataElement.getAttribute(i, colGroup ) ) ;
						// restart the cartesian key at empty
						elementKey.setLength(0);
						// Then add in the proper number of column components 
						elementKey.append( colKeyPiece ).append( DataElement.ROW_COL_SEPARATION_CHAR ) ;
						// Now with the base column done - add each row key, one at a time
						// so get the cartesian of rows & columns into the 
						// elementKey. This inner loop executes once per item in the 
						// cartesian ... 2 row keys & 3 col keys == 6 loops
						for( String rowGroup : rowGroups ) {
							elementKey.append( dataElement.getAttribute( i, rowGroup ) ) ;

							// now we must check if we have a row filter
							// if we do the 1st part of the row key must match the value of the filter
							// and the sub-filter must match the entirfe data element.
							// Example show 1x3 risk per currency
							// the row filter would be the currency and the row filter would 
							// be a list of data conventions matching 1x3 risk
							// Be careful with this it's not very efficient - esp. in the middle of this loop !!!!!
							boolean matchedRowFilter = true ;
							
							if( this.rowFilters!=null ) {
								// we are matching on the value of the attribute, e.g. USD not CCY
								String rowFilterValueToMatch = dataElement.getAttribute( i, rowGroups[0] ) ;
								
								Map<String,String[]> mustMatch = this.rowFilters.get( rowFilterValueToMatch ) ;
								matchedRowFilter &= mustMatch != null ;
								if( matchedRowFilter ) {
									for( String k : mustMatch.keySet() ) {
										String valueToMatch = dataElement.getAttribute( i, k ) ;
										for( String value : mustMatch.get(k) ) {
											matchedRowFilter &= value.equals( valueToMatch ) ;
										}
									}
								} else {
									break ; // optimization to leave the loop early if ot matched (usual case)
								}
							}
							
							if( matchedRowFilter ) {
								// now turn the key into a hashable thing
								String key = elementKey.toString() ;
								DataViewElement dve = dataViewElements.get( key ) ;
								if( dve == null ) {   // if we don't have a key create it
									// Allow concurrent elem creates
									DataViewElement newDve = new DataViewElement() ;
									dve = dataViewElements.putIfAbsent( key, newDve ) ;
									if( dve==null ) {
										dve = newDve ;
									}
								}
								// add the value to the new key
								// This is where the aggregation happens
								dve.add( dataElement.getValue(i) )  ;
							}
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



