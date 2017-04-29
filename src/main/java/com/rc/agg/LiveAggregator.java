package com.rc.agg;

import java.io.IOException;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;
import com.rc.dataview.DataElementStore;
import com.rc.dataview.ViewDefinitions;

import spark.resource.ClassPathResource;

public class LiveAggregator implements DataElementProcessor {
	
	Logger logger = LoggerFactory.getLogger( LiveAggregator.class ) ;

	private final DataElementStore processor ;
	
	public static void main(String[] args) {
		try {
			new LiveAggregator() ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}

	public LiveAggregator() throws IOException {
		Runtime runtime = Runtime.getRuntime();
		logger.info( "Starting aggregator - using {}Mb of RAM", runtime.maxMemory()/0x100000 );

		processor = DataElementStore.getInstance() ;
		URL viewsTxt = getClass().getClassLoader().getResource( "Views.txt" ) ;
		
		ViewDefinitions viewDefinitions = new ViewDefinitions( viewsTxt, processor ) ;
		viewDefinitions.start();
						
		Monitor m = new Monitor() ;
		m.start();
	}
	
	@Override
	public void process( DataElement dataElement ) {
		processor.process(dataElement);
	}

	public void startBatch() {
		processor.startBatch();
	}
	public void endBatch() {
		processor.endBatch();
	}
}
