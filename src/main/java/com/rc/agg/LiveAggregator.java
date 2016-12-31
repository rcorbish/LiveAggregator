package com.rc.agg;

import java.io.IOException;

import com.rc.agg.client.ClientManager;
import com.rc.datamodel.DataElement;
import com.rc.dataview.DataElementStore;
import com.rc.dataview.ViewDefinitions;

public class LiveAggregator implements DataElementProcessor {
	
	private DataElementStore dataElementStore ;
	private ClientManager clientManager ;

	public static void main(String[] args) {
		try {
			new LiveAggregator() ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}

	public LiveAggregator() throws IOException {
		ViewDefinitions viewDefinitions = new ViewDefinitions( "src/main/resources/Views.txt" ) ;
		viewDefinitions.start();
		
		dataElementStore = new DataElementStore() ;
		clientManager = new ClientManager() ;

		WebSocketServer.clientManager = clientManager ;
		dataElementStore.setViewDefinitions( viewDefinitions ) ; 

//		dataElementStore.setDataGridManager( clientManager ) ;
		clientManager.setDataElementStore(dataElementStore) ;
				
		Monitor m = new Monitor() ;
		m.setDataGridManager(clientManager);
		m.start();
		
		dataElementStore.start(); 		
	}
	
	@Override
	public void process( DataElement dataElement ) {
		dataElementStore.process(dataElement);
	}

	public void startBatch() {
		dataElementStore.startBatch();
	}
	public void endBatch() {
		dataElementStore.endBatch();
	}
}
