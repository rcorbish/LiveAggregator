package com.rc.dataview;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;

public class CalculatingDataElementDataView  extends  DataElementDataView {

	final static Logger logger = LoggerFactory.getLogger( CalculatingDataElementDataView.class ) ;

	final float factor  ;

	public CalculatingDataElementDataView( DataElementStore des, ViewDefinition viewDefinition, String arg) {
		super(des, viewDefinition);		
		logger.info( "Arg is {}", arg );
		factor = Float.parseFloat( arg ) ;
	}

	/**
	 * This is the usual process, method. In this case we calculate new
	 * elements from the given one and add both to the data view.
	 */
	@Override
	public void process( DataElement dataElement )  {
		super.process( dataElement );
		DataElement dataElementNew = calculate(dataElement) ;
		if( dataElementNew != null ) {
			super.process( dataElementNew );
		}
	}

	/**
	 * This is a simple Taylor series type calculation, as an example.
	 */
	public DataElement calculate( DataElement dataElement ) {
		DataElement rc = null ;
		DataElement sodDataElement = dataElementStore.get( dataElement.getInvariantKey()+"-SOD" ) ;
		if( sodDataElement != null ) {
			rc = sodDataElement.filteredClone( dataElement.getInvariantKey()+"-P&L", "TYPE", "IR01", "P&L" ) ;
			if( rc != null ) {
				for( int i=0 ; i<rc.size() ; i++ ) {
					rc.set( i, factor * rc.getValue(i) ) ;
				}
			}
			return rc ;
		}
		return rc ;
	}
}



