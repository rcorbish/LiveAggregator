package com.rc.dataview;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.ArrayBlockingQueue ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.DataElementProcessor;
import com.rc.agg.client.ClientCommandProcessorImpl;
import com.rc.datamodel.DataElement;

/**
 * This class represents the view. It maintains a view of
 * all possible (maximally expanded) data elements. Each connected
 * client view will be used to 'filter' down to the subset of
 * expanded cells for a particular client.
 * 
 * It accepts dataViewElements element updates and processes them in 
 * real-time to keep a live view of the model subset. This is the 
 * aggregation step.
 * 
 * @author richard
 *
 */
public class DataElementDataView  implements DataElementProcessor {

	final static Logger logger = LoggerFactory.getLogger( DataElementDataView.class ) ;

	public final static String TOTAL_LABEL = "Total" ;
	
	final DataElementStore dataElementStore ;

	// How often to send an update to the client (millis)
	private static final int CLIENT_UPDATE_INTERVAL = 200 ;	
	private static final int MAX_MESSAGES_TO_BUFFER = 300 ;

	private final Map<String,String[]> filters ; 	// what key = value is being filtered
	private final Map<String,Map<String,String>> setValues ; 	// force change in value of an attribute on condition
	private final String colGroups[] ; 				// what is getting grouped
	private final String rowGroups[] ; 				// what is getting grouped
	private final Set<String> hiddenAttributes ; 	// Do not show these atts on screen
	
	// This contains a list of N int[2] 
	// Each item in the list is a unique permutation of the row index & column index
	// to use for totals. The 1st element is the column, the 2nd is the row index
	private final int totalPermutations[][] ; 		// permutations of col / row indices for totals 
	private final int COLUMN_PERMUTATION_INDEX = 0 ;
	private final int ROW_PERMUTATION_INDEX = 1 ;
	
	// Map keyed on elementKey ( rows & column attribute values )
	// The current (expanded) view is stored in here
	private final Map<String,DataViewElement>   dataViewElements ;	

	private final String viewName ;
	private final String description ;

	private volatile boolean serverBatchComplete ;
	private final List<ClientDataView> clientViews ;	// which clients need to be told about updates?
	private final BlockingQueue<DataElement> messagesToProcess ;
	private Thread messageSender ;
	private Thread messageReceiver ;

	/** Use this to create an instance. If the view definition indicates
	 * a special class that will be used instead of this parent instance.
	 * @param viewDefinition the definition of the View - from config
	 */
	static public DataElementDataView create( DataElementStore dataElementStore, ViewDefinition viewDefinition ) {
		
		Class<? extends DataElementDataView> clazz = viewDefinition.getImplementingClass() ;
		logger.info( "Creating instance of {} for view {}", clazz.getCanonicalName(), viewDefinition.getName() ) ;
		String constructorArg = viewDefinition.getConstructorArg() ;
		DataElementDataView rc = null ;
		try {
			boolean hasArgumentCtor = false ;
			boolean hasCompatibleCtor = false ;
			for( Constructor<?> ctor : clazz.getConstructors() ) {
				Class<?> params[] = ctor.getParameterTypes() ;
				if( params.length>1 && params[0] == DataElementStore.class && params[1] == ViewDefinition.class ) {
					hasCompatibleCtor = true ;
					hasArgumentCtor |= ( params.length>2 && params[2] == String.class ) ;
				}
			}
			if( !hasCompatibleCtor ) {
				throw new RuntimeException( "Cannot find suitable constructor for " + clazz.getCanonicalName() + ". Need (DataElementStore, ViewDefinition ) or (DataElementStore, ViewDefinition, String )" ) ;
			}
			if( hasArgumentCtor && constructorArg != null ) {
				Constructor<? extends DataElementDataView> ctor = clazz.getConstructor( DataElementStore.class, ViewDefinition.class, String.class ) ;
				logger.debug( "Passing {} as constructor arg for view {}", constructorArg, viewDefinition.getName()) ;
				rc = ctor.newInstance( dataElementStore, viewDefinition, constructorArg ) ;
			} else {
				logger.debug( "No constructor arg for view {}", constructorArg, viewDefinition.getName()) ;
				Constructor<? extends DataElementDataView> ctor = clazz.getConstructor( DataElementStore.class, ViewDefinition.class) ;
				rc = ctor.newInstance( dataElementStore, viewDefinition ) ;
			}
		} catch( Exception e ) {
			logger.error( "Failed to create instance of DataElementDataView using new {}( DataElementStore,  {} )", 
							clazz.getCanonicalName(), (constructorArg == null ? "" : constructorArg) ) ;
			throw new RuntimeException( e ) ;
		}
		return rc ;
	}
	
	
	/**
	 * Constructor - parse the view definition into useable data formats
	 * Initialize, but don't start, the threads.
	 * Make sure start() is called at somepoint ...
	 * Don't call this directly - use the static create method. This needs to be public
	 * because of the reflection in the create.
	 * 
	 * @see #create(ViewDefinition)
	 * @see #start()
	 * @param viewDefinition
	 */
	public DataElementDataView( DataElementStore dataElementStore, ViewDefinition viewDefinition ) {

		this.dataElementStore = dataElementStore ;

		this.serverBatchComplete = false ;
		this.clientViews = new ArrayList<>() ;
		this.dataViewElements = new ConcurrentHashMap<>() ;
		this.messagesToProcess = new ArrayBlockingQueue<>( MAX_MESSAGES_TO_BUFFER ) ;
		this.viewName = viewDefinition.getName() ;
		this.description = viewDefinition.getDescription() ;

		//----------------------
		// F I L T E R S
		//
		// if a filter is defined make sure any elements in the view
		// match the filter
		Map<String,String[]> tmp = new HashMap<>() ;
		Map<String,String> rawFilters = viewDefinition.getFilters();
		for( String k : rawFilters.keySet() ) {
			tmp.put( k, DataElement.splitComponents(rawFilters.get(k)) ) ;
		}
		this.filters = tmp.isEmpty() ? null : tmp ;		

		//----------------------
		// S E T   V A L U E S
		//
		// Match a filter? then change value of an attribute
		this.setValues = viewDefinition.getSetValues().isEmpty() ? null : viewDefinition.getSetValues() ; 

		if( viewDefinition.getColGroups().length == 0 ) {
			this.colGroups = new String[] { "" } ; 
		} else {
			this.colGroups = viewDefinition.getColGroups() ;
		}
		if( viewDefinition.getRowGroups().length == 0 ) {  
			this.rowGroups = new String[] { "" } ; 
		} else {
			this.rowGroups = viewDefinition.getRowGroups() ;
		}
		
		this.hiddenAttributes = new HashSet<String>() ;
		for( String hiddenAttribute : viewDefinition.getHiddenAttributes() ) {
			this.hiddenAttributes.add( hiddenAttribute ) ;
		}

		//----------------------
		// T O T A L S
		//
		// Column and rows to total		
		List<Integer> rowTotals = new ArrayList<>() ;
		List<Integer> colTotals = new ArrayList<>() ;
		for( String total : viewDefinition.getTotalAttributes() ) {
			for( int i=0 ; i<this.rowGroups.length ; i++ ) { 
				if( this.rowGroups[i].equals(total) ) rowTotals.add(i) ;  
			}
			for( int i=0 ; i<this.colGroups.length ; i++ ) { 
				if( this.colGroups[i].equals(total) ) colTotals.add(i) ;  
			}
		}
		
		final int numPermutations = (rowTotals.size()+1) * (colTotals.size()+1) - 1 ;
		// This is an array of rowIndex,columnIndex as a 2 element array
		totalPermutations = new int[ numPermutations ][2] ;
		// Create all permutations of row & column totals ( except the null,null )
		// a -1 index means use the input value - do not substitute 'Total' for a component
		int r = rowTotals.size() ;
		int c = colTotals.size() ;
		for( int ix=0 ; ix<numPermutations ; ix++ ) {
			totalPermutations[ix][COLUMN_PERMUTATION_INDEX] = c - 1 ;
			totalPermutations[ix][ROW_PERMUTATION_INDEX] = r - 1 ;
			c-- ;
			if( c < 0 ) {
				r-- ;
				c = colTotals.size() ;
			}
		}		
	}

/**
 * Start the threads running.
 * 
 * We all know not to start during the constructor don't we ? :)
 * 
 */
	public void start() {
		messageSender = new Thread( new Runnable() {
			public void run() {
				senderThread() ;
			}
		} ) ;
		messageSender.start();

		messageReceiver = new Thread( new Runnable() {
			public void run() {
				receiverThread() ;
			}
		} ) ;
		messageReceiver.start() ;
	}

	/**
	 * Shut down the entire data view. It can't receive any more messages.
	 * Also pass on the stop to active clients
	 * 
	 */
	public void stop() {
		if( messageReceiver != null ) {
			messageReceiver.interrupt() ;
			messagesToProcess.clear() ;
		}

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

	/**
	 * Useful for printing sometimes 
	 * 
	 * @return the friendly view name
	 */
	public String getDescription() {
		return description;
	}


	/**
	 * Does a data element match the current filters?
	 * This checks each item in the DataElement for a match
	 * 
	 * @TODO as an optimization decide whether the added complexity
	 * of separating core filters and perimeter filters is worth it
	 * This method does recheck the core filters again (unnecessarily)
	 * 
	 * @param index the index of the perimuter componets in an element
	 * @param element the input DataElement
	 * @return true means we match 
	 */
	private boolean matchesPerimeterElements(int index, DataElement element) {
		boolean rc = true ;
		if( filters != null  ) {
			for( String k : filters.keySet() ) {
				String mustMatchOneOfThese[] = filters.get(k) ;
				String att = element.getAttribute( index, k ) ;
				boolean matchedOneOfthese = false ;
				for( String couldMatchThis : mustMatchOneOfThese ) {
					matchedOneOfthese |= att.equals( couldMatchThis ) ;
					if( matchedOneOfthese ) break ;
				}
				rc &= matchedOneOfthese ;
				if( !rc ) break ;
			}
		}
		return rc ;
	}


	/**
	 * Does a data element match the current filters?
	 * This is actually an optimization, since a lot of
	 * filters occur in the core elements. 
	 * 
	 * @param element
	 * @return
	 */
	private boolean matchesCoreElements(DataElement element) {
		if( filters != null ) {
			//if( !element.quickMatchesCoreKeys( bloomFilter ) ) return false ;
			for( String k : filters.keySet() ) {
				String mustMatchOneOfThese[] = filters.get(k) ;
				// String att = element.getCoreAttribute( k ) ;
				//ZXCVBNM
				String att = element.getAttribute( k ) ;
				if( att != null ) {
					boolean matchedOneOfThese = false ;
					for( String couldMatchThis : mustMatchOneOfThese ) {
						if( att.equals( couldMatchThis ) ) {
							matchedOneOfThese = true ;
							break ;
						}
					}
					if( !matchedOneOfThese ) { 
						return false ;
					}
				}
			}
		}
		return true ;
	}


	/**
	 *  Look at all the saved elements and send any that have changed.
	 *  @TODO Since data can change - in a diff thread - this needs to
	 *  be cleaned up to separate the row keys from the underlying data
	 */
	private synchronized void sendUpdates() {
		if( serverBatchComplete ) {			
			updateTotals();
			
			List<String>	removedKeys = new ArrayList<>() ;
			for( String elementKey : dataViewElements.keySet() ) {
				DataViewElement dve = dataViewElements.get( elementKey ) ;
				if( dve.isUnused() ) {					
					for( ClientDataView cdv : clientViews ) {
						cdv.unusedElement( elementKey ) ;
					}
					removedKeys.add( elementKey ) ;						
				} else if( dve.isUpdated() ) {
					if( !dve.isHidden() ) {
						for( ClientDataView cdv : clientViews ) {
							cdv.updatedElement( elementKey, dve.getValue() ) ;
						}
					}
					dve.clearUpdatedFlag();
				}
			}
			for( String removedKey : removedKeys ) {
				dataViewElements.remove(removedKey) ;
			}
		}
	}

	// This is a helper, Double is immutable - this isn't !!!
	// This is ONLY meant to be used for the method below, don't
	// define it in the method - lower efficiency
	static final class WrappedDouble {
		double d ;
		public WrappedDouble( double d ) { this.d = d ; } 
	}

	/**
	 * This is used to add totals into a view, before it is updated
	 * 
	 */
	protected void updateTotals() {
		if( totalPermutations.length == 0 ) return ;  // optimization for non totalling views
		
		// Keep a running total of 
		Map<String,WrappedDouble> totals = new HashMap<>( dataViewElements.size() ) ;
		
		for( String elementKey : dataViewElements.keySet() ) {
			DataViewElement dve = dataViewElements.get( elementKey ) ;
			if( dve.isTotal() ) continue ;
			Collection<String> totalKeys = makeTotalKeys(elementKey) ;

			for( String totalKey : totalKeys ) {
				WrappedDouble d = totals.get( totalKey ) ;
				if( d == null ) {
					totals.put( totalKey, new WrappedDouble( dve.getValue() ) ) ;
				} else {
					d.d += dve.getValue() ;
				}
			}
		}
		
		for( String totalKey : totals.keySet() ) {			
			DataViewElement dve2 = 	dataViewElements.get( totalKey ) ;
			if( dve2 == null ) {
				dve2 = new DataViewElement( false, true ) ; // a non hidden total element
				dataViewElements.put( totalKey, dve2 ) ;
			}
			dve2.set( totals.get(totalKey).d );
		}
		
	}
	
	
	/**
	 * This makes a key for every permutation of totals for rows and columns. 
	 * We need the permutations since each total can intersect, e.g. a simple case
	 * the top left corner of a grid contains a total of totals
	 * Each key is a copy of the original but replacing the work "Total"
	 * for a totalable component.
	 * 
	 * e.g. if we are totalling on STATE
	 * 
	 * input = Sales\fKY
	 * 
	 * we would return ["Sales\fTotal"]
	 * 
	 * if we are totalling on revenue and State
	 * 
	 * we would return ["Sales\fTotal","Total\fKY","Total\fTotal" ]
	 * 
	 * @param elementKey
	 * @return the list of keys transformed to totals (order is unimportant )
	 */
	protected Collection<String> makeTotalKeys( String elementKey  ) {
		
		List<String> keys = new ArrayList<>() ;

		String components[] = elementKey.split( DataElement.ROW_COL_SEPARATION_STRING ) ;
		String rowKeys = components[1] ; // keys is cols then rows
		String colKeys = components[0] ;

		StringJoiner sjr = new StringJoiner( DataElement.SEPARATION_STRING ) ;
		String keysR[] = rowKeys.split( DataElement.SEPARATION_STRING ) ;
		StringJoiner sjc = new StringJoiner( DataElement.SEPARATION_STRING ) ;
		String keysC[] = colKeys.split( DataElement.SEPARATION_STRING ) ;
		
		for( int p=0 ; p<totalPermutations.length ; p++ ) {
			sjr = new StringJoiner( DataElement.SEPARATION_STRING ) ;
			sjc = new StringJoiner( DataElement.SEPARATION_STRING ) ;
			
			for( int r=0 ; r<keysR.length ; r++ ) {
				sjr.add( ( r==totalPermutations[p][ROW_PERMUTATION_INDEX] ) ? TOTAL_LABEL : keysR[r] ) ;
				
				for( int c=0 ; c<keysC.length ; c++ ) {
					sjc.add( ( c==totalPermutations[p][COLUMN_PERMUTATION_INDEX] ) ? TOTAL_LABEL : keysC[c] ) ;
				}
			}
			keys.add( sjc.toString() + DataElement.ROW_COL_SEPARATION_CHAR + sjr.toString() ) ;
		}
		return keys ;
	}

	/**
	 *  Look at all the saved elements and send any that have changed.
	 *  
	 *  Since data can change - in a diff thread - this needs to
	 *  be cleaned up to separate the row keys from the underlying data
	 */
	public void sendAll( ClientDataView cdv ) {
		for( String elementKey : dataViewElements.keySet() ) {
			DataViewElement dve = dataViewElements.get( elementKey ) ;
			if( !dve.isHidden() ) {
				cdv.updatedElement( elementKey, dve.getValue() ) ;
			}
		}
	}


	/**
	 * Adds an element to the data view. The messages is pre-checked
	 * to see that it matches the view filters.
	 * 
	 * @param dataElement the element to add to the view
	 */
	public void process( DataElement dataElement )  {
		if( matchesCoreElements( dataElement ) && messageReceiver != null ) {
			try {
				messagesToProcess.put( dataElement ) ;
			} catch( InterruptedException iex ) {
				// ignore - interruption means we're shutting down
			}
		}
	}

	/**
	 * Adds an element to the data view. This needs to figure out all the
	 * combinations of keys and add the value to the pre-calculated pieces.
	 * All messages received should be pre-screened so that the core Elements
	 * match any active filters.
	 * 
	 * This method probably consumes 90% of the CPU capacity - be careful editing
	 * 
	 */
	public void receiverThread() {
		Thread.currentThread().setName( "Receiver " + this.getViewName() ) ;
		try {
			// remember the column keys, we need to have a cartesian
			// of row key & column key combinations. 
			StringBuilder colKeyPiece = new StringBuilder( 256 ) ;
			// The cumulative cartesian key for this element
			// need that to keep track of totals
			StringBuilder elementKey = new StringBuilder( 256 ) ;

			while( !Thread.currentThread().isInterrupted() ) {
				//-------------------------------
				// B L O C K I N G  call to take
				// 
				DataElement dataElement = messagesToProcess.take() ;
				// for each sub element
				for( int i=0 ; i<dataElement.size() ; i++ ) {
					//check second part of filter
					if( matchesPerimeterElements( i, dataElement ) ) {
						colKeyPiece.setLength(0);
						// for each column key piece
						boolean hidden = false ;

						for( String colGroup : colGroups ) {
							hidden |= hiddenAttributes.contains( colGroup ) ;
							// add the next piece to the cumulative column key
							String rawColAttributeValue = dataElement.getAttribute( i, colGroup ) ;
							// will we rename any values ( i.e. part of a group ) ?
							if( this.setValues != null ) {
								Map<String,String> setValuesForThisColGroup = this.setValues.get( colGroup ) ;
								if( setValuesForThisColGroup == null ) {  // no renaming defined
									colKeyPiece.append( rawColAttributeValue ) ;
								} else { // rename defined - use the group (or original name)
									String replacementValue = setValuesForThisColGroup.get( rawColAttributeValue ) ;
									colKeyPiece.append( replacementValue==null ? rawColAttributeValue : replacementValue ) ;
								}
							} else { // no grouping defined at all
								colKeyPiece.append( rawColAttributeValue ) ;
							}
							colKeyPiece.append( DataElement.SEPARATION_CHAR ) ;
						}
						colKeyPiece.setLength( colKeyPiece.length() - 1 ) ;
						// restart the cartesian key at empty
						elementKey.setLength(0);
						// Then add in the proper number of column components 
						elementKey.append( colKeyPiece ).append( DataElement.ROW_COL_SEPARATION_CHAR ) ;
						// Now with the base column done - add each row key, one at a time
						// so get the cartesian of rows & columns into the 
						// elementKey. This inner loop executes once per item in the 
						// cartesian ... 2 row keys & 3 col keys == 6 loops
						for( String rowGroup : rowGroups ) {
							hidden |= hiddenAttributes.contains( rowGroup ) ;
							String rawRowAttributeValue = dataElement.getAttribute( i, rowGroup ) ;
							if( this.setValues != null ) {  // any grouping defined?
								Map<String,String> setValuesForThisRowGroup = this.setValues.get( rowGroup ) ;
								if( setValuesForThisRowGroup == null ) { // no groups defined for this section
									elementKey.append( rawRowAttributeValue ) ;
								} else { // group is defined so rename if attribte matches or keep the original
									String replacementValue = setValuesForThisRowGroup.get( rawRowAttributeValue ) ;
									elementKey.append( replacementValue==null ? rawRowAttributeValue : replacementValue ) ;
								}
							} else { 
								elementKey.append( rawRowAttributeValue ) ;  // no groups defined
							}
							elementKey.append( DataElement.SEPARATION_CHAR ) ;
						}
						elementKey.setLength( elementKey.length() - 1 ) ;

						// now turn the key into a hashable thing
						String key = elementKey.toString() ;
						/*
						if( serverBatchComplete ) {
							logger.info( "Processed element key {}", key ) ;
						}
						*/
						DataViewElement dve = dataViewElements.get( key ) ;
						if( dve == null ) {   // if we don't have a key create it
							// Allow concurrent elem creates
							DataViewElement newDve = new DataViewElement( hidden ) ;
							dve = dataViewElements.putIfAbsent( key, newDve ) ;
							if( dve==null ) {
								dve = newDve ;
							}
						}
						// add the value to the new key
						// This is where the aggregation happens
						dve.add( dataElement.getValue(i) )  ; 							
					}
				}
			}
		} catch( InterruptedException iex ) {
			logger.warn( "Message receiver thread interrupted, do NOT send messages to this view.") ;
			messageReceiver = null ;
			messagesToProcess.clear();
		}
	}


	/**
	 * When the server starts a new bntch of data, this is called
	 * it sets a flag - which indicates to suspends messaging to
	 * the clients.
	 * 
	 * @param deleteContents - clear all current data content?
	 */
	public void startBatch( boolean deleteContents ) {		
		serverBatchComplete = false ;
		if( deleteContents ) {
			messagesToProcess.clear();			
			for( DataViewElement dve : dataViewElements.values() ) {
				dve.markUnused() ;
			}
		}
	}

	/**
	 * Restart sending updates to the clients
	 * 
	 */
	public void endBatch() {
		serverBatchComplete = true ;
	}

	public String[] getColGroups() {
		return colGroups;
	}


	public String[] getRowGroups() {
		return rowGroups;
	}

	public boolean isAttributeHidden( String attributeName ) {
		return hiddenAttributes.contains( attributeName ) ;
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

	
	public Map<String,String[]> getFilters() {
		return this.filters ;
	}
	
	public Map<String,Map<String,String>> getSets() {
		return this.setValues ;
	}
	
	public String toString() {
		return viewName + 
				" View Size: " + dataViewElements.size() +
				" Filtered on: " + ( getFilters()==null ? "Nothing!" : ClientCommandProcessorImpl.printArray( getFilters().keySet() ) ) 
				;
	}

	/**
	 * This monitors each element in the cache, sending any changed or
	 * deleted elements to the client. If any client is closed it will 
	 * be deleted from the active client list.
	 * 
	 * The start() method must be called after construction.
	 * 
	 */
	public void senderThread() {
		Thread.currentThread().setName( "Sender " + getViewName() );
		while( messageSender!=null && !messageSender.isInterrupted() ) {
			try {
				Thread.sleep( CLIENT_UPDATE_INTERVAL );
				if( serverBatchComplete ) {
					sendUpdates();
				}

				ListIterator<ClientDataView> iter = clientViews.listIterator();
				while( iter.hasNext() ) {
					ClientDataView cdv = iter.next() ; 
					if(cdv.isClosed() ) {
						iter.remove() ;
						logger.info( "Removing closed ClientDataView {}", cdv ) ;
					}
				}

			} catch( InterruptedException ignore ) {
				break ;
			} catch( Throwable t ) {
				logger.error( "Error sending updates.", t ) ;
			}
		}
		logger.warn( "Message sender for {} is shutdown.", getViewName() ) ;
		messageSender = null ;
	} 

	protected DataElementStore getDataElementStore() {
		return dataElementStore;
	}

}



