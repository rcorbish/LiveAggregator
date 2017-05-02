package com.rc.agg;

import java.io.File;
import java.net.URL;

import com.rc.dataview.DataElementStore;

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

	public void start() {
		try {
			spark.Spark.port( 8111 ) ;
			URL mainPage = getClass().getClassLoader().getResource( "Client.html" ) ;
			File path = new File( mainPage.getPath() ) ;
			spark.Spark.staticFiles.externalLocation( path.getParent() ) ;
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

		rc.append( "<h2>Data store</h2>");
		rc.append( DataElementStore.getInstance().toString() ) ;
		rc.append("<br>") ;
		rc.append( "<h2>Defined Views</h2>");
		rc.append("<ul>") ;
		
		for(  String viewName : DataElementStore.getInstance().getDataViewNames() ) {
				rc
				.append("<li>")
				.append( viewName )
				.append("</li>") ;			
		}
		rc.append( "</ul><hr/>") ;

		rc.append( "<h2>Managed clients</h2>");
		rc.append( WebSocketServer.toStringStatic().replaceAll( "\n", "<br>") );

		rc.append( "</html>");
		return rc ;
	}

	@Override
	public void close() throws Exception {
		spark.Spark.stop() ;
	}

}
