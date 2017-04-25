package com.rc.agg;

import java.text.DecimalFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;

public class LiveAggregatorRandom  {
	Logger logger = LoggerFactory.getLogger( LiveAggregatorRandom.class ) ;

	private DecimalFormat decimalFormat = new DecimalFormat( "#,##0" ) ;

	private LiveAggregator aggregator ;

	private String CCYS[] = new String[] { "USD", "CAD", "EUR", "GBP", "JPY", "SEK", "AUD", "HKD" } ;
	private String TYPES[] = new String[] { "IR01", "NPV", "P&L" } ;
	private String AXES[] = new String[] { "O/N", "1B", "1D", "3D", "1M", "3M", "6M", "9M", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "50Y" } ;
	private String PRODUCTS[] = new String[] { "SWAP", "FRA", "XCCY", "MMKT", "FEE" } ;
	private String BOOKS[] = new String[] { "Book1", "Book2", "Book3", "Book4", "Book5", "Book6" } ;
	private String CPTYS[] = new String[] { "Big Co.", "A Bank", "Bank 2", "Fund 1", "H Fund", "A govt.", "Rand Co." } ;

	private final static String[] ATTRIBUTE_NAMES = new String[] { "TRADEID", "CPTY", "BOOK", "PRODUCT", "TYPE", "AXIS", "CCY" } ; 

	public static void main(String[] args) {
		LiveAggregatorRandom self = null ;
		try {
			self = new LiveAggregatorRandom() ;
			self.aggregator = new LiveAggregator() ;
			self.start() ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}


	public void start() throws Exception {
		final Random random = new Random() ;
		final int UPDATES_PER_MSG = 100 ;
		final int N = 100_000 ;
		final int BATCH_SIZE = 20 ;
		logger.info( "Starting server. Connect to client @ server:8111/Client.html" ); 
		for( ; ; ) {
			long start = System.currentTimeMillis() ;

			ExecutorService executor = Executors.newFixedThreadPool( 2 ) ;
			aggregator.startBatch() ;
			
			// FIRST create an initial view - send updates & stuff
			for( int i=0 ; i<N ; i++ ) {
				final int START = i * BATCH_SIZE ;
				executor.execute(
						new Runnable() {
							public void run() {
								Thread.currentThread().setName( "Start: " + START );
								for( int n=0 ; n<BATCH_SIZE ; n++ ) {
									int ix = START + n ;
									DataElement de = new DataElement(												
											UPDATES_PER_MSG,
											ATTRIBUTE_NAMES,
											new String[] { 
													String.valueOf( ix ),
													CPTYS[ ix % (CPTYS.length - 1) ],
													BOOKS[ ix % (BOOKS.length - 1) ],
													PRODUCTS[ ix % (PRODUCTS.length) ]
											},
											String.valueOf( ix )
											) ;				
									for( int j=0 ; j<de.size() ; j++ ) {
										de.set(j,
												new String[] { 
												TYPES[ ix % (TYPES.length) ],
												AXES[ ix % (AXES.length) ],
												CCYS[ ix % (CCYS.length - 1) ]
										},
										(ix/10_000_000.0) ) ;
									}
									aggregator.process( de ) ;
								}
							}
						} ) ;
			}
			executor.shutdown() ;  // wait for initial view to finish generating
			if( !executor.awaitTermination( 10, TimeUnit.MINUTES ) ) {
				throw new Exception( "Horror of horrors - we timed out waiting for the initial population to finish.");
			}
			logger.info( "Finished processing {} cells in {} mS", decimalFormat.format(N*UPDATES_PER_MSG), decimalFormat.format( (System.currentTimeMillis() - start) ) );
			start = System.nanoTime() ;
			aggregator.endBatch();

			long tPlus5Mins = System.currentTimeMillis() + (5 * 60 * 1000) ;
			
			// Now send random crap for 5 mins
			while( System.currentTimeMillis()<tPlus5Mins ) {
				int ix = random.nextInt( N ) ;

				DataElement de = new DataElement(												
						UPDATES_PER_MSG,
						ATTRIBUTE_NAMES,
						new String[] { 
								String.valueOf( ix ),
								CPTYS[ random.nextInt(CPTYS.length) ],
								BOOKS[ random.nextInt(BOOKS.length) ],
								PRODUCTS[ random.nextInt(PRODUCTS.length) ]
						},
						String.valueOf( ix )
						) ;				
				for( int j=0 ; j<de.size() ; j++ ) {
					de.set(j,
							new String[] { 
							TYPES[ random.nextInt(TYPES.length) ],
							AXES[ random.nextInt(AXES.length) ],
							CCYS[ random.nextInt(CCYS.length) ]
					},
					(random.nextInt( 100 ) - 50)) ;
				}
				aggregator.process( de ) ;									
				Thread.sleep( 15 );  // distance between batch updates
			}
		}
	}
}
