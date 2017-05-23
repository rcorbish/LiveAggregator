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

	private static String CCYS[] = new String[] { "USD", "CAD", "EUR", "GBP", "JPY", "SEK", "AUD", "HKD" } ;
	private static String EVENTS[] = new String[] { "SOD", "AMEND"  } ;
	private static String METRICS[] = new String[] { "IR01", "NPV", "PV" } ;
	private static String TENORS[] = new String[] { "JAN-18", "DEC-19", "2018-12-15", "2017-10-01","2018-02-15","O/N", "1B", "1D", "3D", "1M", "3M", "6M", "9M", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "50Y" } ;
	private static String PRODUCTS[] = new String[] { "SWAP", "FRA", "XCCY", "MMKT", "FEE", "CAP" } ;
	private static String BOOKS[] = new String[] { "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Theta" } ;
	private static String CPTYS[] = new String[] { "Big Co.", "A Bank", "Bank 2", "Fund 1", "H Fund", "A govt.", "Rand Co." } ;

	private final static String[] ATTRIBUTE_NAMES = new String[] { "TRADEID", "CPTY", "BOOK", "PRODUCT", "EVENT", "METRIC", "TENOR", "CCY" } ; 
	private final static int NUM_CORE_ATTRIBUTES = 5 ;
	
	public LiveAggregatorRandom() throws IOException {
		this.aggregator = new LiveAggregator() ;
	}

	public static void main(String[] args) {

/*
		StringJoiner sj = new StringJoiner( "\t" ) ;
		for( int i=0 ; i<BOOKS.length ; i++ ) {
			System.out.println( BOOKS[i] + " " + String.format( "%08x", BOOKS[i].hashCode() & 0x3ff ) ) ;
			sj.setEmptyValue( BOOKS[i] ) ;
			System.out.println( sj + " " + String.format( "%08x", sj.hashCode()  ) ) ;
		}
		System.exit( 1 ); 
*/		
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
		
		boolean sod = true ;
		for( ; ; ) {
			long startTime = System.currentTimeMillis() ;
			logger.info( "Restarting processing of data ..." ) ;
			ExecutorService executor = Executors.newFixedThreadPool( 3 ) ;
			aggregator.startBatch( sod ) ;
			
			final String invariantKeySuffix = sod ? "-SOD" : "";
			
			// FIRST create an initial view - send updates & stuff
			for( int i=0 ; i<N ; i+=BATCH_SIZE ) {
				final int START = i ;
				executor.execute(
						new Runnable() {
							public void run() {
								try { 
									Thread.currentThread().setName( "Test Sender: " + START + "-" + (START+BATCH_SIZE) );
									for( int n=0 ; n<BATCH_SIZE ; n++ ) {
										final String invariantKey = String.valueOf(n+START) ;
										DataElement de = aggregator.get( invariantKey+"-SOD" ) ;
										if( de != null ) {
											String coreValues[] = de.getCoreValues() ;
											coreValues[ dae.getAttributeIndex( "EVENT") ] = "AMEND" ;
											de = de.clone( invariantKey, coreValues ) ;
										} else {
											de = new DataElement(												
												DATA_POINTS_PER_ELEMENT,
												dae,
												new String[] { 
														invariantKey,
														CPTYS[ random.nextInt( CPTYS.length ) ],
														BOOKS[ random.nextInt( BOOKS.length ) ],
														PRODUCTS[ random.nextInt( PRODUCTS.length ) ],
														EVENTS[ invariantKeySuffix.length() > 0 ? 0 : 1 ]
												},
												(invariantKey + invariantKeySuffix)
												) ;				
											for( int j=0 ; j<de.size() ; j++ ) {
												String metric = METRICS[ random.nextInt( METRICS.length ) ] ;
												de.set(j,
														new String[] { 
																metric,
																metric.equals("IR01")?TENORS[ random.nextInt( TENORS.length ) ]:null,
																CCYS[ random.nextInt( CCYS.length ) ]
														},
														(random.nextInt( 1001 ) - 500) / 10.f
														) ;
											}
										}
										for( int j=0 ; j<de.size() ; j++ ) {
											de.set(j, de.getValue(j) + (random.nextInt( 1001 ) - 500) / 100.f ) ;
										}
										aggregator.process( de ) ;
									}
								} catch( Throwable t ) {
									logger.error( ">>>>> Sender test thread error!", t ) ;
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
			sod = false ;

			long tPlus5Mins = System.currentTimeMillis() + (5 * 60 * 1000) ;

			// Now send random crap for 5 mins
			while( System.currentTimeMillis()<tPlus5Mins ) {
				String invariantKey = String.valueOf( random.nextInt( N ) ) ;

				DataElement de = aggregator.get( invariantKey ) ;
				if( de != null ) { 
					de = de.clone() ; 
				} else {
					de = new DataElement(												
						DATA_POINTS_PER_ELEMENT,
						dae,
						new String[] { 
								invariantKey,
								CPTYS[ random.nextInt(CPTYS.length) ],
								BOOKS[ random.nextInt(BOOKS.length) ],
								PRODUCTS[ random.nextInt(PRODUCTS.length) ],
								EVENTS[1]
						},
						invariantKey
						) ;				
					for( int j=0 ; j<de.size() ; j++ ) {
						String metric = METRICS[ random.nextInt( METRICS.length ) ] ;
						de.set(j,
								new String[] { 
										metric,
										metric.equals("IR01")?TENORS[ random.nextInt( TENORS.length ) ]:null,
										CCYS[ random.nextInt(CCYS.length) ]
						},
						(random.nextInt( 1001 ) - 500) / 10.f
						) ;
					}
				}
				for( int j=0 ; j<de.size() ; j++ ) {
					de.set(j, de.getValue(j) + (random.nextInt( 1001 ) - 500) / 100.f ) ;
				}
				aggregator.process( de ) ;
				Thread.sleep( 997 );  // distance between batch updates
			}
		}
	}
}
