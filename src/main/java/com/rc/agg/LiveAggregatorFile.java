package com.rc.agg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.rc.datamodel.DataElement;

public class LiveAggregatorFile  {


	private LiveAggregator aggregator ;

	private String[] ATTRIBUTE_NAMES = null; 
	private final static String VALUE_KEY = "#VALUE#" ;

	public static void main(String[] args) {
		LiveAggregatorFile self = null ;
		try {
			self = new LiveAggregatorFile() ;
			self.aggregator = new LiveAggregator() ;
			self.start() ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}


	public void start() throws InterruptedException {

		File dataFile = new File( "src/main/resources/Test1.txt") ;

		for( ; ; ) {
			aggregator.startBatch();
			for( int n=0 ; n<1 ; n++  ) {
				int lineNumber = 0 ;
				try ( FileReader fr = new FileReader(dataFile) ; BufferedReader br = new BufferedReader( fr ) ) {
					lineNumber++ ;
					String s=br.readLine() ;
					
					if( s!=null ) {
						String cols[] = DataElement.splitComponents(s) ;
						int valueIndex = -1 ;
						for(int i=0 ; i<cols.length ; i++ ) {
							if( cols[i].equals( VALUE_KEY ) ) {						
								valueIndex = i ;
								ATTRIBUTE_NAMES = new String[cols.length-1] ;
								for( int j=0 ; j<ATTRIBUTE_NAMES.length ; j++ ) {
									ATTRIBUTE_NAMES[j] = cols[j<valueIndex?j:(j+1)] ;
								}
								break ;
							}
						}

						if( valueIndex>=0 ) {
							for( s=br.readLine() ; s!=null ; s=br.readLine() ) {
								lineNumber++ ;
								Thread.sleep( 100 ) ;
								if( lineNumber==30 ) {
									aggregator.endBatch();
								}
								s = s.trim() ;
								if( s.charAt(0) == '#' ) {
									continue ;
								}
								cols = DataElement.splitComponents(s) ;
								cols[0] = String.valueOf(n) + cols[0] ;
								String colsWithoutValue[] = new String[cols.length-1] ;
								for( int j=0 ; j<colsWithoutValue.length ; j++ ) {
									colsWithoutValue[j] = cols[j<valueIndex?j:(j+1)] ;
								}
								double value = Double.parseDouble( cols[ valueIndex ] ) ;
								DataElement dataElement = new DataElement( ATTRIBUTE_NAMES, colsWithoutValue, colsWithoutValue[0] ) ;
								dataElement.set( colsWithoutValue, value) ;
//								Thread.sleep( 10 ) ;
								aggregator.process(dataElement);
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
