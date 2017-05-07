package com.rc.agg;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;
import com.rc.datamodel.DataElementAttributes;

public class LiveAggregatorRandom  {
	final static Logger logger = LoggerFactory.getLogger( LiveAggregatorRandom.class ) ;

	private final static DecimalFormat decimalFormat = new DecimalFormat( "#,##0" ) ;

	private final LiveAggregator aggregator ;

	private String CCYS[] = new String[] { "USD", "CAD", "EUR", "GBP", "JPY", "SEK", "AUD", "HKD" } ;
	private String TYPES[] = new String[] { "IR01", "NPV", "P&L" } ;
	private String TENORS[] = new String[] { "O/N", "1B", "1D", "3D", "1M", "3M", "6M", "9M", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "50Y" } ;
	private String PRODUCTS[] = new String[] { "SWAP", "FRA", "XCCY", "MMKT", "FEE", "CAP" } ;
	private String BOOKS[] = new String[] { "Book-1", "Book-2", "Book-3", "Book-4", "Book-5", "Book-6" } ;
	private String CPTYS[] = new String[] { "Big Co.", "A Bank", "Bank 2", "Fund 1", "H Fund", "A govt.", "Rand Co." } ;

	private final static String[] ATTRIBUTE_NAMES = new String[] { "TRADEID", "CPTY", "BOOK", "PRODUCT", "TYPE", "TENOR", "CCY" } ; 
	private final static int NUM_CORE_ATTRIBUTES = 4 ;
	
	public LiveAggregatorRandom() throws IOException {
		this.aggregator = new LiveAggregator() ;
	}

	public static void main(String[] args) {

		LiveAggregatorRandom self = null ;
		try {
			int numBatches = 1_000 ;
			int batchSize = 10 ;
			int dataPointsPerItem = 100 ;
			logger.info( "Reading {} args", args.length ) ;
			if( args.length > 0 ) {
				numBatches = Integer.parseInt( args[0] ) ;
				logger.info( "{} batches", numBatches );

			}
			if( args.length > 1 ) {
				batchSize = Integer.parseInt( args[1] ) ;
				logger.info( "{} batch size", batchSize );
			}
			if( args.length > 2 ) {
				dataPointsPerItem = Integer.parseInt( args[2] ) ;
				logger.info( "{} data points per item", dataPointsPerItem );
			}
			self = new LiveAggregatorRandom() ;
			self.start( numBatches, batchSize, dataPointsPerItem ) ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}


	public void start( int numBatches, int batchSize, int dataPointsPerItem ) throws Exception {

		final DataElementAttributes dae = new DataElementAttributes(ATTRIBUTE_NAMES, NUM_CORE_ATTRIBUTES) ;

		final Random random = new Random( 100 ) ;
		final int DATA_POINTS_PER_ELEMENT = dataPointsPerItem ;
		final int N = numBatches * batchSize ;
		final int BATCH_SIZE = batchSize ;
		logger.info( "Starting server. URL is [server-name]:8111/Client.html" ); 
		for( ; ; ) {
			long startTime = System.currentTimeMillis() ;
			logger.info( "Restarting processing of data" ) ;
			ExecutorService executor = Executors.newFixedThreadPool( 3 ) ;
			aggregator.startBatch() ;

			// FIRST create an initial view - send updates & stuff
			for( int i=0 ; i<N ; i+=BATCH_SIZE ) {
				final int START = i ;
				executor.execute(
						new Runnable() {
							public void run() {
								try { 
									Thread.currentThread().setName( "Start: " + START );
									for( int n=0 ; n<BATCH_SIZE ; n++ ) {
										final String invariantKey = String.valueOf(n+START) ;
										int ix = random.nextInt(17) ;
										DataElement de = new DataElement(												
												DATA_POINTS_PER_ELEMENT,
												dae,
												new String[] { 
														invariantKey,
														CPTYS[ ix % CPTYS.length ],
														BOOKS[ ix % BOOKS.length ],
														PRODUCTS[ ix % PRODUCTS.length ]
												},
												invariantKey
												) ;				
										for( int j=0 ; j<de.size() ; j++, ix++ ) {
											de.set(j,
													new String[] { 
															TYPES[ ix % TYPES.length ],
															TENORS[ ix % TENORS.length ],
															CCYS[ ix % CCYS.length ]
											},
													(random.nextInt( 1001 ) - 500) / 10.f
													) ;
										}
										aggregator.process( de ) ;
									}
								} catch( Throwable t ) {
									logger.error( "Test thread Failed!!!!", t ) ;
								}
							}
						} ) ;
			}
			executor.shutdown() ;  // wait for initial view to finish generating
			if( !executor.awaitTermination( 10, TimeUnit.MINUTES ) ) {
				throw new Error( "Horror of horrors - we timed out (10 mins) waiting for the initial load.");
			}
			logger.info( "Finished processing {} cells in {} mS", decimalFormat.format(N), decimalFormat.format( (System.currentTimeMillis() - startTime) ) );
			startTime = System.nanoTime() ;
			aggregator.endBatch();

			long tPlus5Mins = System.currentTimeMillis() + (5 * 60 * 1000) ;

			//int invariantKey = 0 ;

			// Now send random crap for 5 mins
			while( System.currentTimeMillis()<tPlus5Mins ) {
				String invariantKey = String.valueOf( random.nextInt( N ) ) ;
				DataElement de = new DataElement(												
						DATA_POINTS_PER_ELEMENT,
						dae,
						new String[] { 
								invariantKey,
								CPTYS[ random.nextInt(CPTYS.length) ],
								BOOKS[ random.nextInt(BOOKS.length) ],
								PRODUCTS[ random.nextInt(PRODUCTS.length) ]
						},
						invariantKey
						) ;				
				for( int j=0 ; j<de.size() ; j++ ) {
					de.set(j,
							new String[] { 
									TYPES[ random.nextInt(TYPES.length) ],
									TENORS[ random.nextInt(TENORS.length) ],
									CCYS[ random.nextInt(CCYS.length) ]
					},
							(random.nextInt( 1001 ) - 500) / 10.f
							) ;
				}
				aggregator.process( de ) ;
				Thread.sleep( 997 );  // distance between batch updates
			}
		}
	}
}
