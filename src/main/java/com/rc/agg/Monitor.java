package com.rc.agg;

//import com.rc.agg.client.ClientManager;

import spark.Request;
import spark.Response;

/**
 * This handles the web pages. 
 * 
 * We use spark to server up pages. It's simple and easy to configure. It's pretty basic
 * we need 1 websockt to handle messaging to the client and one static dir for the actual page
 * 
 * @author richard
 *
 */
public class Monitor implements AutoCloseable {

	//ClientManager clientManager ;


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
/*
		rc.append( "<h2>Active clients</h2>");
		
		for( ClientProxy cp : clientManager.getActiveClients()) {
			rc
			.append(cp.toString().replaceAll( "\n", "<br>") )
			.append("<ul>") ;
			for( ClientDataView dedv : cp.getOpenDataGrids().values() ) {
				rc
				.append("<li>")
				.append( dedv.toString() )
				.append("</li>") ;
			}
			rc.append( "</ul><hr/>") ;
		}
*/
		rc.append( "<h2>Managed clients</h2>");
		rc.append( WebSocketServer.toStringStatic().replaceAll( "\n", "<br>") );


		rc.append( "</html>");
		return rc ;
	}



	@Override
	public void close() throws Exception {
		spark.Spark.stop() ;
	}
/*
	public void setDataGridManager(ClientManager clientManager) {
		this.clientManager = clientManager;
	}
*/
}
