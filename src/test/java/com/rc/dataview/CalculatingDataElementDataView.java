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
	public CalculatingDataElementDataView( DataElementStore des, ViewDefinition viewDefinition) {
		this(des, viewDefinition, "1");		
	}

	/**
	 * This is the usual process, method. In this case we calculate new
	 * elements from the given one and add both to the data view.
	 */
	@Override
	public void process( DataElement dataElement )  {
		DataElement dataElementNew = calculate(dataElement) ;
		if( dataElementNew != null ) {
			super.process( dataElementNew );
		}
	}

	/**
	 * This is a simple difference calculation, as an example.
	 */
	public DataElement calculate( DataElement dataElement ) {
		DataElement rc = dataElement ;
		DataElement sodDataElement = dataElementStore.get( rc.getInvariantKey()+"-SOD" ) ;
		if( sodDataElement != null ) {
			rc = dataElement.subtract( sodDataElement ) ;
		}
		return rc ;
	}
}



