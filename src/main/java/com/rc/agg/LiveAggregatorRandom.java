package com.rc.agg;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;
import com.rc.datamodel.DataElementAttributes;

public class LiveAggregatorRandom  {
	final static Logger logger = LoggerFactory.getLogger( LiveAggregatorRandom.class ) ;

	private final static DecimalFormat decimalFormat = new DecimalFormat( "#,##0" ) ;

	private final int numBatches ;
	private final int batchSize ;
	private final int dataPointsPerItem ;
	private final LiveAggregator aggregator ;
	private final String[] ccys;
	private final DataElementAttributes dae;

	private static final String[] CCYS = new String[] { "USD", "CAD", "EUR", "GBP", "JPY", "SEK", "AUD", "HKD" } ;
	private static final String[] EVENTS = new String[] { "SOD", "AMEND"  } ;
	private static final String[] METRICS = new String[] { "IR01", "NPV", "PV" } ;
	private static final String[] TENORS = new String[] { "JAN-18", "DEC-19", "2018-12-15", "2017-10-01","2018-02-15","O/N", "1B", "1D", "3D", "1M", "3M", "6M", "9M", "1Y", "2Y", "3Y", "4Y", "5Y", "6Y", "7Y", "8Y", "9Y", "10Y", "15Y", "20Y", "25Y", "30Y", "50Y" } ;
	private static final String[] PRODUCTS = new String[] { "SWAP", "FRA", "XCCY", "MMKT", "FEE", "CAP" } ;
	private static final String[] BOOKS = new String[] { "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Theta" } ;
	private static final String[] CPTYS = new String[] { "Big Co.", "A Bank", "Bank 2", "Fund 1", "H Fund", "A govt.", "Rand Co." } ;

	private final static String[] ATTRIBUTE_NAMES = new String[] { "TRADEID", "CPTY", "BOOK", "PRODUCT", "EVENT", "METRIC", "TENOR", "CCY" } ; 
	private final static int NUM_CORE_ATTRIBUTES = 5 ;

	final Random random = new Random( 100 ) ;

	final AtomicInteger tid;

	public static void main(String[] args) {

        LiveAggregatorRandom self;
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
			self = new LiveAggregatorRandom( numBatches, batchSize, dataPointsPerItem ) ;
			self.start() ;
		} catch( Throwable t ) {
			t.printStackTrace();
			System.exit( -1 ) ;
		}
	}

	public LiveAggregatorRandom( int numBatches, int batchSize, int dataPointsPerItem )  throws IOException {
		this.numBatches = numBatches;
		this.batchSize = batchSize;
		this.dataPointsPerItem = dataPointsPerItem;
		final int N = numBatches * batchSize ;
		ccys =  random.ints(N,0,CCYS.length).mapToObj(ix -> CCYS[ix]).toArray(String[]::new);
		dae = new DataElementAttributes(ATTRIBUTE_NAMES, NUM_CORE_ATTRIBUTES) ;
		this.aggregator = new LiveAggregator() ;
		this.tid = new AtomicInteger(0);
	}

	public void start() throws Exception {

		final int N = numBatches * batchSize ;
		logger.info( "Starting server. URL is [server-name]:8111/Client.html" );
		
		while( true ) {
			long startTime = System.currentTimeMillis() ;
			logger.info( "Restarting processing of data ..." ) ;
			tid.set(0);
			aggregator.startBatch(true);
			try ( ExecutorService executor = Executors.newFixedThreadPool( 6 ) ) {
				for (int i = 0; i < N; i ++) {
					executor.execute( this::createOne );
				}

				executor.shutdown();  // wait for initial view to finish generating
				if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
					throw new Error("Horror of horrors - we timed out (10 mins) waiting for the initial load.");
				}
			}
			logger.info( "Finished processing {} cells in {} mS", decimalFormat.format(N), decimalFormat.format( (System.currentTimeMillis() - startTime) ) );
			aggregator.endBatch();

			try( ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor() ) {
				executorService.scheduleAtFixedRate(this::sendOne, 0, 10, TimeUnit.MILLISECONDS);
				// Now send random crap for 5 mins
				TimeUnit.MINUTES.sleep(5);
				executorService.shutdown();
				if( !executorService.awaitTermination(10, TimeUnit.MINUTES) ) {
					logger.warn("Hmmmm - scheduler didn't finish properly.");
				}
			}
		}
	}

	public void createOne() {
		final int key = tid.getAndIncrement();
		final String invariantKey = String.valueOf(key);

		DataElement de = new DataElement(
				dataPointsPerItem,
				dae,
				new String[]{
						invariantKey,
						CPTYS[random.nextInt(CPTYS.length)],
						BOOKS[random.nextInt(BOOKS.length)],
						PRODUCTS[random.nextInt(PRODUCTS.length)],
						EVENTS[0]
				},
				(invariantKey + "-SOD")
		);
		for (int j = 0; j < de.size(); j++) {
			String metric = METRICS[random.nextInt(METRICS.length)];
			de.set(j,
					new String[]{
							metric,
							metric.equals("IR01") ? TENORS[random.nextInt(TENORS.length)] : null,
							ccys[key]
					},
					(random.nextInt(1001) - 500) / 10.f
			);
		}

		for (int j = 0; j < de.size(); j++) {
			de.set(j, de.getValue(j) + (random.nextInt(1001) - 500) / 100.f);
		}
		de.sanitize();
		aggregator.process(de);
	}


	public void sendOne() {
		final int N = numBatches * batchSize ;

		int tid = random.nextInt( N );
		String invariantKey = tid + "-AMEND" ;

		DataElement de = aggregator.get( invariantKey ) ;
		if( de != null ) {
			de = de.clone() ;
		} else {
			de = new DataElement(
					dataPointsPerItem,
					dae,
					new String[] {
							String.valueOf(tid),
							CPTYS[ random.nextInt(CPTYS.length) ],
							BOOKS[ random.nextInt(BOOKS.length) ],
							PRODUCTS[ random.nextInt(PRODUCTS.length) ],
							EVENTS[1]
					},
					invariantKey
			) ;
			String ccy = ccys[tid] ;
			for( int j=0 ; j<de.size() ; j++ ) {
				String metric = METRICS[ random.nextInt( METRICS.length ) ] ;
				de.set(j,
						new String[] {
								metric,
								metric.equals("IR01")?TENORS[ random.nextInt( TENORS.length ) ]:null,
								ccy
						},
						(random.nextInt( 1001 ) - 500) / 10.f
				) ;
			}
		}
		for( int j=0 ; j<de.size() ; j++ ) {
			de.set(j, de.getValue(j) + (random.nextInt( 1001 ) - 500) / 100.f ) ;
		}
		de.sanitize();
		aggregator.process( de ) ;
	}
}
