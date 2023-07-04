package com.rc.dataview;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.DataElementProcessor;
import com.rc.datamodel.DataElement;
import com.rc.datamodel.DataElementAttributes;

/**
 * This is the cache of all currently valid data. It's big and stupid and fast!
 * Any data element that might contribute to a view will be cached in here.
 * 
 * This is a singleton.
 * 
 * @author richard
 *
 */
public class DataElementStore  implements DataElementProcessor {

	final static Logger logger = LoggerFactory.getLogger( DataElementStore.class ) ;
	private final static DataElementStore instance = new DataElementStore() ;

	private final Map<String,DataElement> 	currentElements ;
	private boolean							serverBatchComplete ;
	private Map<String,DataElementDataView>	availableViews ;		// current available views
	private int								numberDrillThroughs ;
	private final Date						startedAt ;
	
	/**
	 * The singleton constructor. Sets up a huge hash map to store data
	 */
	private DataElementStore() {
		currentElements =  new ConcurrentHashMap<>( 5_000_011 ) ;
		availableViews = new HashMap<>() ;
		numberDrillThroughs = 0 ;
		startedAt = new Date() ;
	}


	/**
	 * This is a singleton - and this is how you get it. 
	 * @return the singleton instance
	 */
	public static DataElementStore getInstance() {
		return instance ;
	}

	/**
	 * Indicate whether we have received an endBatch notification.
	 * @return have we received an end notification ?
	 */
	public synchronized boolean isServerBatchComplete() {
		return serverBatchComplete;
	}

	/**
	 * Empty all elements from the cache. Used at start batch.
	 * 
	 */
	public void clear() {		
		currentElements.clear(); 
	}

	/**
	 * The start of it all - when a data store gets notification of new information
	 * this is it. 
	 * 
	 * Call this and everything just works :)
	 * 
	 */
	public void process(DataElement dataElement) {
		DataElement previous = currentElements.put( dataElement.getInvariantKey(), dataElement) ;
		if( previous != null ) {
			DataElement negatedCopy = previous.negatedCopy() ;
			for( DataElementDataView dedv : availableViews.values() ) {
				dedv.process( negatedCopy ) ;
				dedv.process( dataElement ) ;
			}
		} else {
			for( DataElementDataView dedv : availableViews.values() ) {
				dedv.process( dataElement ) ;
			}
		}
	}

	/**
	 * When a view changes during processing we need to replay
	 * all current data points to all (new) views. This will 
	 * interrupt processing.
	 * 
	 */
	protected synchronized void reprocess() throws InterruptedException {
		// If we're in the middle of reprocessing a batch
		// don't do anything, let the current batch finish on
		// its own.
		if( serverBatchComplete ) {
			for( DataElement dataElement : currentElements.values() ) {
				for( DataElementDataView dedv : availableViews.values() ) {
					dedv.process( dataElement ) ;
				}
			}
			endBatch();
		}
	}

	/**
	 * Start a new batch - clear out existing data.
	 * Note this is synchronized (with reprocess). We can't
	 * reprocess and resend a batch at the same time.
	 * 
	 * @param deleteContents remove all current data elements ?
	 */
	public synchronized void startBatch( boolean deleteContents ) {
		// prevent updates during initial population
		serverBatchComplete = false ;
		// remove any existing (old) data?
		if( deleteContents ) {
			clear() ;
		}

		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.startBatch( deleteContents ) ;
		}
	}

	/**
	 * When a batch is done each view is notified, which usually
	 * indicates all clients receive a new version of the aggregated data
	 */
	public synchronized void endBatch() {
		serverBatchComplete = true ;
		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.endBatch() ;
		}
	}

	
	public void setViewDefinitions(ViewDefinitions viewDefinitions) {

		logger.info( "Updating view definitions." );

		Map<String,DataElementDataView>	futureAvailableViews = new HashMap<>() ; 

		for( ViewDefinition vd : viewDefinitions.getViewDefinitions() ) {
			DataElementDataView dedv = DataElementDataView.create( this, vd ) ;			
			futureAvailableViews.put( dedv.getViewName(), dedv ) ;
		}

		logger.info( "Shutting down existing views." );

		Collection<DataElementDataView> oldViews = availableViews.values() ; 

		for( DataElementDataView existingDedv : oldViews ) {
			existingDedv.resetAndStop() ;
		}
		availableViews = futureAvailableViews ;

		start() ;
		try {
			reprocess();
		} catch( InterruptedException itsOK ) {
			logger.info( "Thread interrupted, better be shutting down" ) ;
		}
	}

	/**
	 * Make sure this is called after creating the receiver,
	 * otherwise no data will flow downstream. It passes
	 * on the requests to each view, where all the real 
	 * multi-threading happens.
	 * 
	 */
	public void start() {
		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.start();
		}
	}

	/**
	 * Get an individual data view
	 * 
	 * @param name the unique name of the view
	 * @return the DataView used to cache a pre-aggregated report
	 */
	public DataElementDataView getDataElementDataView( String name ) {
		return availableViews.get( name ) ;
	}

	/**
	 * Return the set of view names - used by GUI to build a list
	 * of available reports
	 * 
	 * @return the list of individual view names
	 */
	public Collection<String> getDataViewNames() {
		return availableViews.keySet() ;
	}

	/**
	 * Get a DataElement by its invariant key
	 * 
	 * @param invariantKey
	 * @return the matching DataElement ( or null if not found )
	 */
	public DataElement get( String invariantKey ) {
		return currentElements.get( invariantKey ) ;
	}

	/**
	 * How many data elements are being held, i.e. how many 
	 * different invariant keys exist.
	 * 
	 * @return The amount of stored data elements
	 */
	public int size() {
		return currentElements.size() ;
	}
	
	
	/**
	 * Return a collection of data points that match the query. 
	 * The query uses the same separators as an element key (@see DataElement)
	 * 
	 * A query is formed like this ...
	 * <code> 
	 *  ATT-1=VAL-1\tVAL-2\fATT-2=VAL-6
	 * </code> 
	 *  
	 * Because it is often passed in via URL it's OK to use this ...
	 * <code> 
	 *  ATT-1=VAL-1%09VAL-2%0cATT-2=VAL-6
	 * </code> 
	 * 
	 * Any element matching ...
	 * <code> 
	 * 	ATT-1 is either VAL-1 or VAL-2
	 * </code>?
	 * <b>and</b>
	 * <code> 
	 *  ATT-2 is VAL-6
	 * </code>?
	 * The return is a Collection of String arrays, which can be formatted nicely on
	 * a web page (for example). 
	 * 
	 * example return ( 3 items in a Collection )
	 * <code>
	 * 	[	[TRADEID,CCY,BOOK,METRIC,TENOR,VALUE],
	 * 		[trade-1,USD,Book6,NPV,N/A,100],
	 * 		[trade-1,USD,Book6,NPV,N/A,100],
	 * 		[trade-1,USD,Book6,NPV,N/A,100] ]
	 * </code>
	 * It is mandatory to <i>limit</i> the maximum number of rows returned.
	 * It is <b>assumed</b? all elements in the array have the same attributes. This 
	 * is the usual, but not mandatory, case. The first element in the result is
	 * the attribute names (of the first matching element).
	 * 
	 * The view name is used to find additional filters for the view.
	 * 
	 * Because, this app is highly concurrent, be aware that if deriving the 
	 * filter from a live report, this may return elements which are different
	 * from what is exactly in the view. Basically we can't connect the end view and input
	 * cache in real-time. There is latency in the forward pass. Its intent is to be
	 * used to drill-down into a report cell.
	 * 
	 * @param query the query string ( e.g. trade-1\tUSD\tBook6\tNPV\tN/A\t100 )
	 * @param viewName the name of the view requesting data
	 * @param limit maximum number of items to return
	 * @return A Collection of items representing a single value, An empty collection perhaps? The 1st row is the attribute names
	 */
	public Collection<DataDetailMessage> query( String query, String viewName, int limit ) {

		List<DataDetailMessage> rc = new ArrayList<>(limit*2) ;

		DataElementDataView dedv = getDataElementDataView( viewName ) ;
		if( dedv == null ) {
			logger.warn( "Invalid view name '{}' passed to query data.", viewName ) ;
			return rc ;
		}

		numberDrillThroughs++ ; // for monitoring activity
		if( currentElements.isEmpty() ) return rc ;    // not exactly thread safe - but not much else we can do

		//
		// The filters against which to test each data point
		//
		// 	key = attribute name
		// 	value = strings - one of which must match the data element attribute value
		//
		Map<String,Set<String>>matchingTests = new HashMap<>() ;

		//-----------------------------------------
		//
		// Parse the input query into a filter set
		//
		String[] elementKeys = query.split( String.valueOf( DataElement.ROW_COL_SEPARATION_CHAR ) ) ;
        for (String elementKey : elementKeys) {
            int ix = elementKey.indexOf('=');
            if (ix > 0) {
                String key = elementKey.substring(0, ix);
				String value = elementKey.substring(ix + 1);
				if( !value.equals(DataElementDataView.TOTAL_LABEL)) {
					Set<String> values = new HashSet<>();
					Collections.addAll(values, DataElement.splitComponents(value));
					matchingTests.put(key, values);
				}
            }
        }
		
		//
		// Add any additional filters defined by the view itself into the 
		// matchingTests. The filter MAY already be part of the query so 
		// this is a merge of the two filters
		//
		
		Map<String,String[]>viewFilters = dedv.getFilters() ;
		if( viewFilters != null ) { // possibly no filters set
			for( var entry : viewFilters.entrySet() ) {
				Set<String> queryAttributeValues = matchingTests.get( entry.getKey() ) ;
				
				if( queryAttributeValues == null ) {
					queryAttributeValues = new HashSet<>() ;
					matchingTests.put( entry.getKey() , queryAttributeValues ) ;
				} else {
					continue ;   // if the view allows many values for a key - but we know which one leave it alone
				}
				String[] viewAttributeValues = entry.getValue();
				queryAttributeValues.addAll(Arrays.asList(viewAttributeValues));
			}
		}

		//
		// If we have a set defined we should unmap the set name to its constituents
		// Leave the original in the test, it's unlikely but possible to rename 
		// a vkey to an existing key, which might be in the view. And a set test is crazy fast
		//
		Map<String,Map<String,String>>viewSets = dedv.getSets() ;
		if( viewSets != null ) {
			for( var entry : viewSets.entrySet() ) {
				Set<String> queryAttributeValues = matchingTests.get( entry.getKey() ) ;
				if( queryAttributeValues == null ) {
					continue ; // no matching attribute == not involved in filter
				}
				
				Map<String,String> viewAttributeSets = entry.getValue() ;
				logger.info( "Examining set {} => {}", entry.getKey(), viewAttributeSets ) ;

				for( var entry2 : viewAttributeSets.entrySet() ) {
					String viewRemappedTo = entry2.getValue() ;
					if( queryAttributeValues.contains( viewRemappedTo ) ) {
						queryAttributeValues.add( entry2.getKey() ) ;
					}
				}
			}
		}



		// 
		// OK one last thing - if we added a 'dummy' row or column into the view
		// to, for example, make a total row/col, we better remove it. We need to
		// check each attribute for reality.
		//
		Set<String> allKeys = new HashSet<>() ;
		String[] colGroups = dedv.getColGroups();
		String[] rowGroups = dedv.getRowGroups();
        Collections.addAll(allKeys, colGroups);
        Collections.addAll(allKeys, rowGroups);
		logger.info( "Scanning these keys {} to see whether they are synthetic.", allKeys ) ;
		DataElement de = currentElements.values().iterator().next() ;
		DataElementAttributes dae = de.getDataElementAttributes() ;
		Set<String> notRealAttributes = new HashSet<>() ;
		for( String requestedAttributeName : allKeys ) {
			int ix = dae.getAttributeIndex(requestedAttributeName) ;
			if( ix < 0 ) {
				notRealAttributes.add( requestedAttributeName ) ;
			}	
		}
		for( String notRealAttribute : notRealAttributes ) {
			matchingTests.remove( notRealAttribute ) ;
		}
		logger.info( "Ignoring unreal keys {} => testing for {}.", notRealAttributes, matchingTests ) ;
		
		
		//
		// OK now for the large scan of the concurrent hash map
		// scan for anything that matches our filter. Add matching
		// elements and keep the ones with the largest values to return.
		//
		String[] attributeNames = dae.getAttributeNames() ;
		for( DataElement value : currentElements.values() ) {
			
			// If this is older than the oldest in the list forget it
			// The time applies to the whole element - so we can optimize out a loop
			boolean decidedToAddToList;

			if( value.matchesCoreKeys( matchingTests ) ) {	
				for( int i=0 ; i<value.size() ; i++ ) {				
					if( value.matchesPerimeterKeys(i, matchingTests)) {
						// 2 reasons to add - either we haven't filled up the list
						// or the current value is younger than the oldest in the 
						// current list ( see above part of the test )
						//
						// We do 2x for optimization - so we sort less frequently 
						// If it's faster to sort than add each time this can be changed
						// i.e. maintain a sorted list ( priority-queue ? )
						decidedToAddToList = rc.size() < (2*limit) ;		
						
						if(decidedToAddToList) {
							DataDetailMessage ddm = new DataDetailMessage( value, i ) ;
							rc.add( ddm ) ;
							// We won't chop the list every time. Keep a bigger list
							// to reduce sorting. We can clean up at the end
							if( rc.size() >= (2*limit) ) {
								Collections.sort( rc ) ;
								while( rc.size() > limit ) {
									rc.remove( limit ) ;
								}
							}
						}
					}
				}
			}
		}
		
		Collections.sort( rc ) ;
		while( rc.size() > limit ) {
			rc.remove( limit ) ;
		}
        //
		// Add in a header row if we found anything.
		//
		if( attributeNames != null ) {
			DataDetailMessage hdrs = new DataDetailMessage( attributeNames ) ;
			rc.add( 0, hdrs ) ;
		}
		
		return rc ;
	}

	/**
	 * Useful for debugging ...
	 * 
	 */
	public String toString() {
		return "Data Store containing " + currentElements.size() + 
		" elements\nBatch is " + (serverBatchComplete? "complete.\n" : "processing.\n") +
		numberDrillThroughs + " drillthroughs requested\nServer started " + startedAt ; 
	}
}
