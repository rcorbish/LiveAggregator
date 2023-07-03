package com.rc.agg;

import java.io.IOException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;
import com.rc.dataview.DataElementStore;
import com.rc.dataview.ViewDefinitions;


public class LiveAggregator implements DataElementProcessor, AutoCloseable {
	
	final static Logger logger = LoggerFactory.getLogger( LiveAggregator.class ) ;

	private final DataElementStore dataElementStore ;
	private final Monitor webServer ;
	private final ViewDefinitions viewDefinitions ;

	public LiveAggregator() throws IOException {
		Runtime runtime = Runtime.getRuntime();
		logger.info( "Starting aggregator - using {}Mb of RAM", runtime.maxMemory()/0x100000 );

		this.dataElementStore = DataElementStore.getInstance() ;
		URL viewsTxt = getClass().getClassLoader().getResource( "Views.txt" ) ;
		if( viewsTxt==null ) {
			throw new RuntimeException("Cannot find resource views.txt in classpath" ) ;
		}
		viewDefinitions = new ViewDefinitions( viewsTxt, this.dataElementStore ) ;
		webServer = new Monitor() ;
		viewDefinitions.start();
		webServer.start();
	}
	
	@Override
	public void process( DataElement dataElement ) {
		this.dataElementStore.process(dataElement);
	}
	
	public DataElement get( String invariantKey ) {
		return dataElementStore.get(invariantKey) ;
	}

	public void startBatch( boolean deleteContents ) {
		this.dataElementStore.startBatch( deleteContents );
	}
	public void endBatch() {
		this.dataElementStore.endBatch();
	}

	public int size() {
		return this.dataElementStore.size() ;
	}

	public void close() {
		try {
			webServer.close();
			viewDefinitions.close() ;
		} catch( Throwable t ) {
			logger.warn( "Error shutting down aggregator.", t ) ;
		}
		logger.info( "Aggregator finished. B Y E  B Y E") ;
	}
}
