package com.rc.dataview;

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

	public void process(DataElement dataElement) throws InterruptedException {
		DataElement previous = currentElements.put( dataElement.getInvariantKey(), dataElement) ;
		if( previous != null ) {
			DataElement negatedCopy = previous.negatedCopy() ;
			for( DataElementDataView dedv : availableViews.values() ) {
				dedv.process( negatedCopy ) ;
			}
		}
		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.process( dataElement ) ;
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
			DataElementDataView dedv = new DataElementDataView( vd ) ;			
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
	 * @return
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
	 * Because, this app is highly concurrent, be aware that if deriving the 
	 * filter from a live report, this may return elements which are different
	 * than what is exactly in the view. Basically we can't connect the end view and input
	 * cache in real-time. There is latency in the forward pass. Its intent is to be
	 * used to drill-down into a report cell.
	 * 
	 * @param query the query string ( e.g. trade-1\tUSD\tBook6\tNPV\tN/A\t100 )
	 * @param limit maximum number of items to return
	 * @return A Collection of Strings, An empty collection perhaps. The 1st row may be empty if no elements match
	 */
	public Collection<String[]> query( String query, int limit ) {
		List<String[]> rc = new ArrayList<>(limit+1) ;
		String elementKeys[] = query.split( String.valueOf( DataElement.ROW_COL_SEPARATION_CHAR ) ) ;
		Map<String,Set<String>>matchingTests = new HashMap<>() ;
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
		
		String attributeNames[] = null ;
		Comparator<String[]> comparator = new Comparator<String[]>() {
			@Override
			public int compare(String[] o1, String[] o2) {
				float f = Math.abs( Float.parseFloat(o2[0]) ) - Math.abs( Float.parseFloat(o1[0]) ) ;
				return f<0 ? -1 : 1 ;
			}
		};
		float currentMax = 0.f ;
		for( DataElement value : currentElements.values() ) {
			if( attributeNames == null ) attributeNames = value.getAttributeNames() ;
			for( int i=0 ; i<value.size() ; i++ ) {
				boolean matchedAllKeys = true ;
				for( String attributeName : matchingTests.keySet() ) {
					Set<String> attributeValues = matchingTests.get( attributeName ) ;
					matchedAllKeys &= attributeValues.contains( value.getAttribute(i,attributeName ) ) ;
				}
				if( matchedAllKeys ) {
					String tmp[] = new String[ attributeNames.length + 1] ;
					int ix = 1 ;
					for( String valueAttributeName : attributeNames ) {
						tmp[ix] = value.getAttribute( i, valueAttributeName ) ;
						ix++ ;
					}
					boolean decidedToAddToList = rc.size() < limit ;
					if( Math.abs(value.getValue(i)) > currentMax ) {
						currentMax = Math.abs(value.getValue(i)) ;
						decidedToAddToList = true;
					}
					if(decidedToAddToList) {
						tmp[0] = String.valueOf( value.getValue(i) ) ;
						rc.add( tmp ) ;
						rc.sort(comparator);
						while( rc.size() >= limit ) {
							rc.remove( limit - 1 ) ;
						} ;
					}
				}
			}
		}
		
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
		return "Data Store containing " + currentElements.size() + " elements."  ; 
	}
}
