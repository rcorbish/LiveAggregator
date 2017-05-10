package com.rc.dataview;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.DataElementProcessor;
import com.rc.datamodel.DataElement;
import com.rc.datamodel.DataElementAttributes;

/**
 * This is the cache of all currently valaid data. It's big and stupid and fast!
 * Any data element that might contribute to a view will be cached in here.
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

	/**
	 * The singleton constructor. Sets up a huge hash map to store data
	 */
	private DataElementStore() {
		currentElements =  new ConcurrentHashMap<>( 7_000_000 ) ;
		availableViews = new HashMap<>() ;
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
	public boolean isServerBatchComplete() {
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
	 * reprocess & resend a batch at the same time.
	 * 
	 */
	public synchronized void startBatch() {
		// prevent updates during initial population
		serverBatchComplete = false ;
		// remove any existing (old) data
		clear() ;

		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.startBatch() ;
		}
	}

	/**
	 * When a batch is done each view is notified, which usually
	 * indicates all clients receive a new version of the aggregated data
	 */
	public void endBatch() {		
		serverBatchComplete = true ;
		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.endBatch() ;
		}
	}

	public void setViewDefinitions(ViewDefinitions viewDefinitions) {

		logger.info( "Updating view definitions." );

		Map<String,DataElementDataView>	futureAvailableViews = new HashMap<>() ; 

		for( ViewDefinition vd : viewDefinitions.getViewDefinitions() ) {
			DataElementDataView dedv = DataElementDataView.create( vd ) ;			
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
	 * than what is exactly in the view. Basically we can't connect the end view and input
	 * cache in real-time. There is latency in the forward pass. Its intent is to be
	 * used to drill-down into a report cell.
	 * 
	 * @param query the query string ( e.g. trade-1\tUSD\tBook6\tNPV\tN/A\t100 )
	 * @param viewName the name of the view requesting data
	 * @param limit maximum number of items to return
	 * @return A Collection of Strings, An empty collection perhaps. The 1st row may be empty if no elements match
	 */
	public Collection<String[]> query( String query, String viewName, int limit ) {

		List<String[]> rc = new ArrayList<>(limit+1) ;
		if( currentElements.size() == 0 ) return rc ;    // not exactly thread safe - but not much else we can do

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
		String elementKeys[] = query.split( String.valueOf( DataElement.ROW_COL_SEPARATION_CHAR ) ) ;
		for( int i=0 ; i<elementKeys.length ; i++ ) {
			int ix = elementKeys[i].indexOf( '=' ) ;
			if( ix>0 ) {
				String key = elementKeys[i].substring(0,ix) ;
				Set<String> values = new HashSet<>() ;
				for( String v : DataElement.splitComponents( elementKeys[i].substring(ix+1) ) ) {
					values.add(v) ; 
				}
				matchingTests.put( key, values ) ;
			}
		}  
		
		//
		// Add any additional filters defined by the view itself into the 
		// matchingTests. The filter MAY already be part of the query so 
		// this is a merge of the two filters
		//
		DataElementDataView dedv = getDataElementDataView( viewName ) ;
		if( dedv == null ) {
			logger.warn( "Invalid view name '{}' passed to query data.", viewName ) ;
			return rc ;
		}
		
		Map<String,String[]>viewFilters = dedv.getFilters() ;
		if( viewFilters != null ) { // possibly no filters set
			for( String attributeName : viewFilters.keySet() ) {
				Set<String> queryAttributeValues = matchingTests.get( attributeName ) ;
				
				if( queryAttributeValues == null ) {
					queryAttributeValues = new HashSet<>() ;
					matchingTests.put( attributeName, queryAttributeValues ) ;
				} else {
					continue ;   // if the view allows many values for a key - but we know which one leave it alone
				}
				String viewAttributeValues[] = viewFilters.get( attributeName ) ;
				for( String viewAttributeValue : viewAttributeValues ) {
					queryAttributeValues.add( viewAttributeValue ) ;				
				}
			}
		}

		//
		// If we have a set defined we should unmap the set name to it's constituents
		// Leave the original in the test, it's unlikely but possible to rename 
		// a vkey to an existing key, which might be in the view. And a set test is crazy fast
		//
		Map<String,Map<String,String>>viewSets = dedv.getSets() ;
		if( viewSets != null ) {
			for( String attributeName : viewSets.keySet() ) {
				Set<String> queryAttributeValues = matchingTests.get( attributeName ) ;
				
				if( queryAttributeValues == null ) {
					queryAttributeValues = new HashSet<>() ;
					matchingTests.put( attributeName, queryAttributeValues ) ;
				}
				
				Map<String,String> viewAttributeSets = viewSets.get( attributeName ) ;

				boolean matchedASetValue = false ;
				for( String viewRemappedFrom : viewAttributeSets.keySet() ) {
					String viewRemappedTo = viewAttributeSets.get( viewRemappedFrom ) ;
					if( queryAttributeValues.contains( viewRemappedTo ) ) {
						queryAttributeValues.add( viewRemappedFrom ) ;
						matchedASetValue = true ;
					}
				}
				if( !matchedASetValue && queryAttributeValues.contains( "Other" ) ) {
					logger.info( "Will not match Other set values. Work to do here for negative tests" ) ;
				}
			}
		}

		// 
		// OK one last thing - if we added a 'dummy' row or column into the view
		// to, for example, make a total row/col, we better remove it. We need to
		// check each attribute for reality.
		//
		Set<String> allKeys = new HashSet<>() ;
		String colGroups[] = dedv.getColGroups();
		String rowGroups[] = dedv.getRowGroups();
		for( String s : colGroups ) allKeys.add(s) ;
		for( String s : rowGroups ) allKeys.add(s) ;
		logger.info( "Scanning for these keys {}", allKeys ) ;
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
		
		
		//
		// This is used to sort the data, we will only keep the limit
		// largest values in the return. We don't want to draw too many
		// things on screen, so we choose the biggest values to show
		//
		Comparator<String[]> comparator = new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				float f = Math.abs( Float.parseFloat(o2[0]) ) - Math.abs( Float.parseFloat(o1[0]) ) ;
				return f<0 ? -1 : ( f>0 ) ? 1 : 0  ;
			}
		};
		
		//
		// OK now for the large scan of the concurrent hash map
		// scan for anything that matches our filter. Add matching
		// elements and keep the ones with the largest values to return.
		//
		String attributeNames[] = dae.getAttributeNames() ;
		float currentMax = 0.f ;
		for( DataElement value : currentElements.values() ) {
			if( value.matchesCoreKeys( matchingTests ) ) {		
				for( int i=0 ; i<value.size() ; i++ ) {				
					if( value.matchesPerimiterKeys(i, matchingTests)) {
						String tmp[] = new String[attributeNames.length + 1] ;
						int ix = 1 ;
						for( String valueAttributeName : attributeNames ) {
							tmp[ix] = value.getAttribute( i, valueAttributeName ) ;
							ix++ ;
						}
						// 2 reasons to add - either we haven't filled up the list
						// or the current value is bigger than the smallest in the 
						// current list
						boolean decidedToAddToList = rc.size() < limit ;
						if( Math.abs(value.getValue(i)) > currentMax ) {
							decidedToAddToList = true;
						}
						if(decidedToAddToList) {
							tmp[0] = String.valueOf( value.getValue(i) ) ;
							rc.add( tmp ) ;
							// We won't chop the list every time. Keep a bigger list
							// to reduce sorting. We can clean up at the end
							if( rc.size() > (2*limit) ) {
								rc.sort( comparator ) ;
								while( rc.size() > limit ) {
									rc.remove( limit - 1 ) ;
								} ;
								String s[] = rc.get( limit-1 ) ;
								currentMax = Math.abs( Float.parseFloat(s[0]) ) ;
							}
						}
					}
				}
			}
		}
		
		rc.sort( comparator ) ;
		while( rc.size() > limit ) {
			rc.remove( limit - 1 ) ;
		} ;
		//
		// Make the numbers pretty - to print in a report
		DecimalFormat numberFormatter = new DecimalFormat( "#,##0;(#,##0)") ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.get(i)[0] = numberFormatter.format( Float.parseFloat( rc.get(i)[0] ) ) ;
		}		
		//
		// Add in a header row if we found anything.
		//
		if( attributeNames != null ) {
			String tmp[] = new String[ attributeNames.length + 1] ;
			for( int i=1 ; i<tmp.length ; i++ ) {
				tmp[i] = attributeNames[i-1] ;						
			}
			tmp[0] = "Value" ;
			rc.add( 0, tmp ) ;
		}
		
		return rc ;
	}

	/**
	 * Useful for debugging ...
	 * 
	 */
	public String toString() {
		return "Data Store containing " + currentElements.size() + 
		" elements. Batch is " + (serverBatchComplete? "complete." : "processing.")  ; 
	}
}
