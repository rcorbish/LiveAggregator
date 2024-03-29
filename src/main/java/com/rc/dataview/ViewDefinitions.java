package com.rc.dataview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * This looks after reading the config file to define views. If the config file
 * changes, this jobby will wake up and reset the views. Note that the old view is
 * still valid as long as someone has it open. New users will NOT be allowed to
 * see a view that's been changed though.
 * 
 * @author richard
 *
 */
public class ViewDefinitions implements Runnable, AutoCloseable {
	final static Logger logger = LoggerFactory.getLogger( ViewDefinitions.class ) ;

	private static final int PERIOD_FOR_CHECKING_VIEWS_FILE  = 15000 ;

	private final File viewDefinitionFile ;
	private volatile Thread fileWatcherThread  ;
	private Map<String,ViewDefinition> viewDefinitions ;

	private final DataElementStore dataElementStore ;

	public ViewDefinitions( URL viewDefinitions, DataElementStore dataElementStore ) throws IOException {
		this( viewDefinitions.getFile(), dataElementStore ) ;
	}

	public ViewDefinitions( String viewDefinitions, DataElementStore dataElementStore ) throws IOException {
		this.viewDefinitionFile = new File( viewDefinitions ) ;
		this.dataElementStore = dataElementStore ;

		if( !viewDefinitionFile.canRead() ) {
			throw new IOException( "File '" + viewDefinitionFile + "' cannot be read." ) ;
		}		
	}


	/**
	 * I know there is a FileWatcher - but it has problems with
	 * remote drives. Anyway this works :)
	 */
	public void start() throws IOException {
		if( fileWatcherThread != null ) {
			logger.warn( "FileWatcher Thread was already running - requested kill." ) ;
			fileWatcherThread.interrupt();
		}
		final Map<String,ViewDefinition> potentialViewDefinitions = loadViewDefinitions();
		commitViewDefinitions( potentialViewDefinitions ) ;
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
		fileWatcherThread.setName( "View File Watcher" );
        long loadedFileTimestamp = viewDefinitionFile.lastModified() ;
		try {
			while( fileWatcherThread!= null && !fileWatcherThread.isInterrupted() ) {

				if( viewDefinitionFile.lastModified() != loadedFileTimestamp ) {
					for( ; ; )  { 
						try {
							long currentFileTimestamp = viewDefinitionFile.lastModified() ;
							Map<String,ViewDefinition> potentialViewDefinitions = loadViewDefinitions() ;
							long currentFileTimestamp2 = viewDefinitionFile.lastModified() ;
							if( currentFileTimestamp != currentFileTimestamp2 ) {
								throw new IOException( "File contents changed during load - reloading" ) ;
							}
							commitViewDefinitions( potentialViewDefinitions ) ;
							loadedFileTimestamp = currentFileTimestamp ;
							break ;
						} catch( IOException error ) {
							logger.error( "Error parsing updated views. Previous definitions remain in effect until this is fixed.", error ) ;
							Thread.sleep( 3000 );
						}
					} 
				}				
				Thread.sleep( PERIOD_FOR_CHECKING_VIEWS_FILE );
			}
		} catch ( InterruptedException ignore ) {

		}
	}

	private void commitViewDefinitions( Map<String,ViewDefinition> potentialViewDefinitions ) {
		viewDefinitions = potentialViewDefinitions ;
		logger.info( "Committed new definitions: {} views available.", viewDefinitions.size() ) ;
		dataElementStore.setViewDefinitions( this ) ;
	}

	private synchronized Map<String,ViewDefinition> loadViewDefinitions() throws IOException {
		Map<String,ViewDefinition> potentialViewDefinitions = new HashMap<>() ;

		Pattern viewDefinitionPattern = Pattern.compile( "([^\\.]+)\\.([^\\=]+)\\=(.*)" ) ;
		int lineNum = 0 ;
		try (FileInputStream fis = new FileInputStream(viewDefinitionFile);
             Reader r = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader(r)) {
			for( String s=br.readLine() ; s != null ; s=br.readLine()) {
				lineNum++ ;
				s = s.trim() ;
				if(s.isEmpty() || s.charAt(0)=='#' ) {	// comment or empty line
					continue ;
				}
				if( s.equals("END") ) {
					logger.info("Early end to views.txt - END at line {}", lineNum ) ;
					break ;
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
					} else if( item.equalsIgnoreCase("HIDE") ) {
						viewDefinition.addHiddenAttribute(value); 
					} else if( item.equalsIgnoreCase("TOTAL") ) {
						viewDefinition.addTotalAttribute(value); 
					} else if( item.equalsIgnoreCase("DESC") ) {
						viewDefinition.setDescription(value); 
					} else if( item.equalsIgnoreCase("CLASS") ) {
						viewDefinition.setImplementingClassName(value); 
					} else if( item.equalsIgnoreCase("CLASS_ARG") ) {
						viewDefinition.setConstructorArg(value); 
					} else if( item.equalsIgnoreCase("FILTER") ) {
						String[] kv = value.split( "=" ) ;
						if( kv.length<2) {
							throw new IOException( "Invalid filter specified at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
						}
						viewDefinition.addFilter( kv[0], kv[1] ) ; 
					} else if( item.equalsIgnoreCase("SET") ) {
						String[] kv = value.split( "=" ) ;
						if( kv.length<3) {
							throw new IOException( "Invalid set value specified at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
						}
						viewDefinition.addSetValue( kv[0], kv[1], kv[2] ) ; 
					} else {
						throw new IOException( "Unrecognized element definition at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
					}
				} else {
					throw new IOException( "Invalid view definition element at " + viewDefinitionFile.getAbsolutePath() + " line #" + lineNum ) ;
				}
			}
		}		
	return potentialViewDefinitions ;
	}
}
