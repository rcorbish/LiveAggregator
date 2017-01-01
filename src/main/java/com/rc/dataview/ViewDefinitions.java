package com.rc.dataview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.agg.WebSocketServer;

public class ViewDefinitions implements Runnable, AutoCloseable {
	Logger logger = LoggerFactory.getLogger( ViewDefinitions.class ) ;

	private File viewDefinitionFile ;
	private volatile Thread fileWatcherThread  ;
	private Map<String,ViewDefinition> viewDefinitions ;
	private Map<String,ViewDefinition> potentialViewDefinitions ;
	
	public ViewDefinitions( String fileName ) throws IOException {
		viewDefinitionFile = new File( fileName ) ;
		if( !viewDefinitionFile.canRead() ) {
			throw new IOException( "File '" + fileName + "' cannot be read." ) ;
		}		
	}
	
	public void start() throws IOException {
		if( fileWatcherThread != null ) {
			logger.warn( "FileWatcher Thread was already running - requested kill." ) ;
			fileWatcherThread.interrupt();
		}
		loadViewDefinitions();
		commitViewDefinitions();
		fileWatcherThread = new Thread( this ) ;
		fileWatcherThread.start(); 
	}
	
	@Override
	public void close() {
		if( fileWatcherThread != null ) {
			fileWatcherThread.interrupt();
			fileWatcherThread = null ;
		}
	}

	public ViewDefinition getViewDefinition( String name ) {
		return viewDefinitions==null ? null : viewDefinitions.get( name ) ;
	}

	public ViewDefinition[] getViewDefinitions() {
		return viewDefinitions==null ? new ViewDefinition[0] : viewDefinitions.values().toArray( new ViewDefinition[0] ) ;
	}
	
	public void run() {
		fileWatcherThread.setName( "View File Watcher" ); ;
		long loadedFileTimestamp = viewDefinitionFile.lastModified() ;
		try {
			while( fileWatcherThread!= null && !fileWatcherThread.isInterrupted() ) {
				Thread.sleep( 15000 );
				
				if( viewDefinitionFile.lastModified() != loadedFileTimestamp ) {
					for( ; ; )  { 
						try {
							long currentFileTimestamp = viewDefinitionFile.lastModified() ;
							loadViewDefinitions() ;
							long currentFileTimestamp2 = viewDefinitionFile.lastModified() ;
							if( currentFileTimestamp != currentFileTimestamp2 ) {
								throw new IOException( "File contents changed during load - reloading" ) ;
							}
							commitViewDefinitions() ;
							loadedFileTimestamp = currentFileTimestamp ;
							break ;
						} catch( IOException error ) {
							logger.error( "Error parsing updated views. Previous definitions remain in effect until this is fixed.", error ) ;
							Thread.sleep( 3000 );
						}
					} 
				}
			}
		} catch ( InterruptedException ignore ) {
			
		}
	}

	private void commitViewDefinitions() {
		viewDefinitions = potentialViewDefinitions ;
		logger.info( "Committed new definitions: {} views available.", viewDefinitions.size() ) ;
	}

	private synchronized void loadViewDefinitions() throws IOException {
		potentialViewDefinitions = new HashMap<>() ;
		
		Pattern viewDefinitionPattern = Pattern.compile( "([^\\.]+)\\.([^\\=]+)\\=(.*)" ) ;
		int lineNum = 0 ;
		try ( Reader r = new FileReader(viewDefinitionFile) ) {
			 BufferedReader br = new BufferedReader(r) ;
			 for( String s=br.readLine() ; s != null ; s=br.readLine()) {
				 lineNum++ ;
				 s = s.trim() ;
				 if( s.length()==0 || s.charAt(0)=='#' ) {	// comment or empty line
					 continue ;
				 }
				 Matcher m = viewDefinitionPattern.matcher( s ) ;
				 if( m.matches() ) {
					 String viewName = m.group(1) ;
					 String item = m.group(2) ;
					 String value = m.group(3) ;
					 ViewDefinition viewDefinition = potentialViewDefinitions.get(viewName) ;
					 if( viewDefinition==null ) {
						 viewDefinition = new ViewDefinition(viewName);
						 potentialViewDefinitions.put(viewName, viewDefinition) ;
					 }
					 if( item.equalsIgnoreCase("COL") ) {
						 viewDefinition.addColGroup(value); 
					 } else if( item.equalsIgnoreCase("ROW") ) {
						 viewDefinition.addRowGroup(value); 
					 } else if( item.equalsIgnoreCase("DESC") ) {
						 viewDefinition.setDescription(value); 
					 } else if( item.equalsIgnoreCase("FILTER") ) {
						 String kv[] = value.split( "=" ) ;
						 if( kv.length<2) {
							 throw new IOException( "Invalid filter specified at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
						 }
						 viewDefinition.addFilter( kv[0], kv[1] ) ; 
					 } else {
						 throw new IOException( "Unrecognized element definition at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
					 }
				 } else {
					 throw new IOException( "Invalid view definition element at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
				 }
			 }
		}		
	}
}
