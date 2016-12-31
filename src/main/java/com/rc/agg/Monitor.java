package com.rc.agg;

import com.rc.agg.client.ClientManager;
import com.rc.agg.client.ClientProxy;
import com.rc.dataview.ClientDataView;

import spark.Request;
import spark.Response;

public class Monitor implements AutoCloseable {

	ClientManager clientManager ;


	public void start() {
		try {
			spark.Spark.port( 8111 ) ;
			spark.Spark.staticFiles.externalLocation( "src/main/resources" );
			spark.Spark.webSocket("/live", WebSocketServer.class);
			spark.Spark.get( "/", this::index ) ;
			spark.Spark.awaitInitialization() ;
		} catch( Exception ohohChongo ) {

		}
	}

	// by default print active clients
	public Object index(Request req, Response rsp) {
		StringBuilder rc = new StringBuilder( "<html>" ) ;

		rsp.type( "text/html" );

		rc.append( "<h2>Active clients</h2>");
		for( ClientProxy cp : clientManager.getActiveClients()) {
			rc
			.append( "<h3>")
			.append(cp.toString())
			.append("</h3>") ;
			for( ClientDataView dedv : cp.getOpenDataGrids().values() ) {
				rc
				.append("<h5>")
				.append( dedv.toString() )
				.append("</h5>") ;
			}
		}

		rc.append( "</html>");
		return rc ;
	}



	@Override
	public void close() throws Exception {
		spark.Spark.stop() ;
	}

	public void setDataGridManager(ClientManager clientManager) {
		this.clientManager = clientManager;
	}

}
