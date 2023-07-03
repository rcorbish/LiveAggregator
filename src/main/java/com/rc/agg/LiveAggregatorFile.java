package com.rc.agg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;
import com.rc.datamodel.DataElementAttributes;

/**
 * This reads a csv file and fires the attributes into an aggregator.
 * The csv header ro defines the attribute names. A special column 
 * named "#VALUE#" is used as the data element value. All other columns 
 * are labels
 * 
 * @author richard
 *
 */
public class LiveAggregatorFile  {

	final static Logger logger = LoggerFactory.getLogger( LiveAggregatorFile.class ) ;

	private final static String VALUE_KEY = "#VALUE#" ;

	private final LiveAggregator aggregator ;

	public static void main(String[] args) {
		LiveAggregatorFile self ;
		try {
			self = new LiveAggregatorFile() ;
			String fileName = args.length>0 ? args[0] : "src/main/resources/Test1.txt" ;
			self.start( fileName ) ;
		} catch( Throwable t ) {
			logger.error( t.getMessage() );
			System.exit( -1 ) ;
		}
	}

	public LiveAggregatorFile() throws IOException {
		this.aggregator = new LiveAggregator() ;
	}

	public void start( String fileName ) throws InterruptedException {

		File dataFile = new File( fileName ) ;
		logger.info( "Starting to process {}", dataFile ); 
		
		aggregator.startBatch( true );
		int lineNumber = 0 ;
		
 		try (FileInputStream fis = new FileInputStream(dataFile);
             Reader r = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader br = new BufferedReader( r ) ) {
			lineNumber++ ;
			String s=br.readLine() ;

			if( s!=null ) {
				String[] attributeNames = null ;
				String[] cols = DataElement.splitComponents(s) ;
				int valueIndex = -1 ;
				for(int i=0 ; i<cols.length ; i++ ) {
					if( cols[i].equals( VALUE_KEY ) ) {						
						valueIndex = i ;
						attributeNames = new String[cols.length-1] ;
						for( int j=0 ; j<attributeNames.length ; j++ ) {
							attributeNames[j] = cols[j<valueIndex?j:(j+1)] ;
						}
						break ;
					}
				}
				if( attributeNames == null ) {
					throw new RuntimeException("No data elements to aggregate");
				}
				final DataElementAttributes dae = new DataElementAttributes(attributeNames, attributeNames.length) ;

				for( s=br.readLine() ; s!=null ; s=br.readLine() ) {
					lineNumber++ ;
					if( lineNumber==30 ) {
						aggregator.endBatch();
					}
					s = s.trim() ;
					if( s.charAt(0) == '#' ) {
						continue ;
					}
					cols = DataElement.splitComponents(s) ;
					String[] colsWithoutValue = new String[cols.length-1] ;
					for( int j=0 ; j<colsWithoutValue.length ; j++ ) {
						colsWithoutValue[j] = cols[j<valueIndex?j:(j+1)] ;
					}
					float value = Float.parseFloat( cols[ valueIndex ] ) ;
					DataElement dataElement = new DataElement( dae, colsWithoutValue, colsWithoutValue[0] ) ;
					dataElement.set( colsWithoutValue, value) ;
					aggregator.process(dataElement);
				}
			}
			logger.info( "Finished processing {} lines of {}", lineNumber, dataFile ) ;  
		} catch (IOException e) {
			logger.error( "Error processing input file.", e );
		}
	}
}
