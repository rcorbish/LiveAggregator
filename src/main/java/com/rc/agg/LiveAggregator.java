package com.rc.agg;

import java.io.IOException;

import com.rc.datamodel.DataElement;
import com.rc.dataview.DataElementStore;
import com.rc.dataview.ViewDefinitions;

public class LiveAggregator implements DataElementProcessor {
	
	DataElementStore processor ;
	
	public static void main(String[] args) {
		try {
			new LiveAggregator() ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}

	public LiveAggregator() throws IOException {
		processor = DataElementStore.getInstance() ;
		ViewDefinitions viewDefinitions = new ViewDefinitions( "src/main/resources/Views.txt", processor ) ;
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
