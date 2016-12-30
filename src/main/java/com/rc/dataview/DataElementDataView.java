package com.rc.dataview;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.rc.agg.DataElementProcessor;
import com.rc.datamodel.DataElement;

/**
 * This class represents the client view grid. It accepts dataViewElements element updates
 * and processes them in real-time
 * 
 * @author richard
 *
 */
public class DataElementDataView  implements DataElementProcessor, Runnable {

	private final Map<String,String> filters ; 		// what key = value is being filtered
	private final List<String> colGroups ; 			// what is getting grouped
	private final List<String> rowGroups ; 			// what is getting grouped

	private final Map<String,DataViewElement>   dataViewElements ;	// raw dataViewElements that needs to be updated is stored here
	private final String viewName ;
	private final String description ;
	public String getDescription() {
		return description;
	}

	private volatile boolean serverBatchComplete ;
	private final List<ClientDataView> clientViews ;
	private Thread messageSender ;


	public DataElementDataView( 
			ViewDefinition viewDefinition ) {

		this.viewName = viewDefinition.getName() ;
		this.description = viewDefinition.getDescription() ;
		this.filters = viewDefinition.getFilters();

		if( viewDefinition.getColGroups().isEmpty() ) {
			colGroups = new ArrayList<>() ; 
			colGroups.add("--") ; 
		} else {
			this.colGroups = viewDefinition.getColGroups() ;
		}
		if( viewDefinition.getRowGroups().isEmpty() ) {
			rowGroups = new ArrayList<>() ; 
			rowGroups.add("--") ; 
		} else {
			this.rowGroups = viewDefinition.getRowGroups() ;
		}

		this.clientViews = new ArrayList<>() ;

		dataViewElements = new ConcurrentHashMap<>() ;
	}

	public void start() {
		messageSender = new Thread( this ) ;
		messageSender.start();
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
				String []mustMatchOneOfThese = mustMatchSomethingInHere.split("\t" ) ;
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
				String []mustMatchOneOfThese = mustMatchSomethingInHere.split("\t" ) ;
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
	 * Call this to reset - ie. clear the entire view
	 */
	public synchronized void reset() {
		dataViewElements.clear();
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
						elementKey.append( colKeyPiece ).append( '\f') ;
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
							elementKey.append( '\t' ) ;
						}					
						colKeyPiece.append( '\t') ;
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


	public List<String> getColGroups() {
		return colGroups;
	}


	public List<String> getRowGroups() {
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
				Thread.sleep( 150 );
				if( serverBatchComplete ) {
					sendUpdates();
				}
			} catch( InterruptedException ignore ) {
				break ;
			} catch( Throwable t ) {
				System.out.println( "Error sending updates: " + t.getLocalizedMessage() ) ;
				t.printStackTrace();
			}
		}
	} 
}



