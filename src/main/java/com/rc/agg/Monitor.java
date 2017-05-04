package com.rc.agg;

import java.io.File;
import java.net.URL;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.rc.dataview.DataElementStore;


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
	
	final static Logger logger = LoggerFactory.getLogger( Monitor.class ) ;
	final static String ELEMENT_KEY_PARAM = "element-key" ;
	final static String LIMIT_PARAM = "limit" ;
	final Gson gson ;

	{
		gson = new Gson();
	}
	
	public void start() {
		try {
			spark.Spark.port( 8111 ) ;
			URL mainPage = getClass().getClassLoader().getResource( "Client.html" ) ;
			File path = new File( mainPage.getPath() ) ;
			spark.Spark.staticFiles.externalLocation( path.getParent() ) ;
			spark.Spark.webSocket("/live", WebSocketServer.class);
			spark.Spark.get( "/", this::index ) ;
			spark.Spark.get( "/data/:" + ELEMENT_KEY_PARAM, this::dataElements, gson::toJson ) ;
			spark.Spark.awaitInitialization() ;
		} catch( Exception ohohChongo ) {

		}
	}

	public Object dataElements(Request req, Response rsp) {
		Object rc = null ;
		try {
			rsp.type( "application/json" );

			String elementKey = req.params( ELEMENT_KEY_PARAM ) ;
			String tmp = req.queryParams(LIMIT_PARAM) ;
			int limit = Integer.parseInt(tmp) ;
			logger.info( "Querying for {} - max {} items", elementKey, limit ) ;
			DataElementStore des = DataElementStore.getInstance() ;
			Collection<String[]> matching = des.query(elementKey, limit) ;
			rc = matching ;
			logger.info( "Found {} items", matching.size()>0?matching.size()-1:0  ) ;
		} catch ( Throwable t ) {
			logger.warn( "Error processing data request", t ) ;
			rc = "Orig URL = " + req.url() + "<br>Should be ... /data/key?limit=nnn" ;
			rsp.status( 400 ) ;	
		}
		return rc ;
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

		rc.append( "<h2>Sample Data query</h2>");
		rc.append( "<a href='http://" + req.host() + "/data/CCY=HKD%09AUD%0CBOOK=Book6?limit=100'>http://" + req.host() + "/data/CCY=HKD%09AUD%0CBOOK=Book6?limit=100</a>") ;
		rc.append( "</html>");
		return rc ;
	}

	@Override
	public void close() throws Exception {
		spark.Spark.stop() ;
	}

}
