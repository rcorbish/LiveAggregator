package com.rc.dataview;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.DataElementProcessor;
import com.rc.datamodel.DataElement;

/**
 * This is the cache of all currently valaid data. It's big and stupid and fast!
 * Any data element that might contribute to a view will be cached in here.
 * 
 * @author richard
 *
 */
public class DataElementStore  implements DataElementProcessor {

	Logger logger = LoggerFactory.getLogger( DataElementStore.class ) ;
	
	private final Map<String,DataElement> 	currentElements ;
	private boolean							serverBatchComplete ;
	private Map<String,DataElementDataView>	availableViews ;		// current available views

	private DataElementStore() {
		currentElements =  new ConcurrentHashMap<>( 7_000_000 ) ;
		availableViews = new HashMap<>() ;
	}

	
	private static DataElementStore instance = new DataElementStore() ;
	 
	public static DataElementStore getInstance() {
		return instance ;
	}
	
	public boolean isServerBatchComplete() {
		return serverBatchComplete;
	}

	public void clear() {		
		currentElements.clear(); 
	}

	public void process(DataElement dataElement) {
		DataElement previous = currentElements.put( dataElement.getInvariantKey(), dataElement) ;
		if( previous != null ) {
			for( DataElementDataView dedv : availableViews.values() ) {
				dedv.process( previous.negatedCopy() ) ;
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
	protected synchronized void reprocess() {
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
		reprocess();
	}

	public void start() {
		for( DataElementDataView dedv : availableViews.values() ) {
			dedv.start();
		}
	}

	public DataElementDataView getDataElementDataView( String name ) {
		return availableViews.get( name ) ;
	}

	public Collection<String> getDataViewNames() {
		return availableViews.keySet() ;
	}
	
	public String toString() {
		return "Data Store containing " + currentElements.size() + " elements."  ; 
	}
}
