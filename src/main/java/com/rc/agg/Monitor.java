package com.rc.agg;

import java.io.File;
import java.net.URL;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.rc.datamodel.DataElement;
import com.rc.dataview.DataElementStore;


import spark.Request;
import spark.Response;

/**
 * This handles the web pages. 
 * 
 * We use spark to serve pages. It's simple and easy to configure. It's pretty basic
 * we need 1 websockt to handle messaging to the client and one static dir for the actual page
 * 
 * @author richard
 *
 */
public class Monitor implements AutoCloseable {
	
	final static Logger logger = LoggerFactory.getLogger( Monitor.class ) ;
	final static String ELEMENT_KEY_PARAM = "element-key" ;
	final static String VIEW_NAME_PARAM = "view-name" ;
	final static String LIMIT_PARAM = "limit" ;
	final static String INVARIANT_KEY_PARAM = "invariant-key" ;
	
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
			spark.Spark.get( "/item/:" + INVARIANT_KEY_PARAM, this::getItem, gson::toJson ) ;
			spark.Spark.awaitInitialization() ;
		} catch( Exception ohohChongo ) {

		}
	}

	public Object getItem(Request req, Response rsp) {
		Object rc = null ;
		try {
			rsp.type( "application/json" );
			rsp.header("expires", "0" ) ;
			rsp.header("cache-control", "no-cache" ) ;

			String invariantKey = java.net.URLDecoder.decode( req.params( INVARIANT_KEY_PARAM ), "UTF-8" ) ;
			logger.info( "Querying for invariant-key {}", invariantKey ) ;
			DataElementStore des = DataElementStore.getInstance() ;
			DataElement matching = des.get(invariantKey) ;
			rc = matching ;
			if( rc==null ) {
				logger.info( "No items matching key {}", invariantKey ) ;
			}
		} catch ( Throwable t ) {
			logger.warn( "Error processing getItem request", t ) ;
			rc = "Orig URL = " + req.url() + "<br>Should be ... /item/inv-key" ;
			rsp.status( 400 ) ;	
		}
		return rc ;
	}

	public Object dataElements(Request req, Response rsp) {
		Object rc = null ;
		try {
			rsp.type( "application/json" );
			rsp.header("expires", "0" ) ;
			rsp.header("cache-control", "no-cache" ) ;

			String elementKey = java.net.URLDecoder.decode( req.params( ELEMENT_KEY_PARAM ), "UTF-8" ) ;
			String viewName = req.queryParams( VIEW_NAME_PARAM ) ;			
			if( viewName == null ) {
				throw new Exception( "Missing parameter - " + VIEW_NAME_PARAM ) ;
			}
			String tmp = req.queryParams(LIMIT_PARAM) ;
			int limit = Integer.parseInt(tmp) ;
			logger.info( "Querying for {} in view {} - max {} items", elementKey.replaceAll("\f", "|"), viewName, limit ) ;
			DataElementStore des = DataElementStore.getInstance() ;
			Collection<String[]> matching = des.query(elementKey, viewName, limit) ;
			rc = matching ;
			logger.info( "Found {} items", matching.size()>0?matching.size()-1:0  ) ;
		} catch ( Throwable t ) {
			logger.warn( "Error processing data request", t ) ;
			rc = "Orig URL = " + req.url() + "<br>Should be ... /data/key?limit=nnn&view-name=aaa" ;
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
				.append( DataElementStore.getInstance().getDataElementDataView(viewName) )
				.append("</li>") ;			
		}
		rc.append( "</ul><hr/>") ;

		rc.append( "<h2>Managed clients</h2>");
		rc.append( WebSocketServer.toStringStatic().replaceAll( "\n", "<br>") );

		rc.append( "<h2>Sample Data query</h2>") ; 
		rc.append( "This is a sample - adjust the URL to your own case<br>" ) ;
		rc.append( "<a target='data-window' href='http://" + req.host() + "/data/CCY=HKD%09AUD%0CBOOK=Book6?limit=100&view-name=DG0'>Sample query</a>") ;
		rc.append( "</html>");
		return rc ;
	}

	@Override
	public void close() throws Exception {
		spark.Spark.stop() ;
	}

}
