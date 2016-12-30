package com.rc.dataview;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.rc.agg.DataElementProcessor;
import com.rc.agg.client.ClientManager;
import com.rc.datamodel.DataElement;


public class DataElementStore  implements DataElementProcessor {

	private final Map<String,DataElement> 	currentElements ;
//	private ClientManager 					clientManager ;	
	private boolean							serverBatchComplete ;
	private Map<String,DataElementDataView>	availableViews ;


	public boolean isServerBatchComplete() {
		return serverBatchComplete;
	}

	public DataElementStore() {
		currentElements =  new ConcurrentHashMap<>( 7_000_000 ) ;
		availableViews = new HashMap<>() ;
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

	public void startBatch() {
		serverBatchComplete = false ;
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

	public void completeBatch() {
	}

	public void getAllData( final DataElementProcessor processor  ) {
		ExecutorService executor = Executors.newFixedThreadPool( 8 ) ;

		for( final DataElement de :currentElements.values() ) {
			executor.execute( new Runnable() { public void run() { processor.process(de) ; } } ) ;
		}
		executor.shutdown();
		try {
			executor.awaitTermination(10, TimeUnit.MINUTES ) ;
		} catch (InterruptedException ignore) {		}
	}

//	public void setDataGridManager(ClientManager clientManager) {
//		this.clientManager = clientManager;
//	}
//	
	
	public void setViewDefinitions(ViewDefinitions viewDefinitions) {
		for( ViewDefinition vd : viewDefinitions.getViewDefinitions() ) {
			DataElementDataView dedv = new DataElementDataView(vd) ;
			this.availableViews.put( dedv.getViewName(), dedv ) ;
		}
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
}
