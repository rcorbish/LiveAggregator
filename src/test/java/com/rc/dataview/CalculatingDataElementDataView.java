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

	
	public DataElement calculate( DataElement dataElement ) {
		DataElement rc = null ;
		DataElement sodDataElement = dataElementStore.get( dataElement.getInvariantKey()+"-SOD" ) ;
		if( sodDataElement != null ) {
			rc = sodDataElement.clone( dataElement.getInvariantKey()+"-P&L", "TYPE", "IR01", "P&L" ) ;
			if( rc != null ) {
				for( int i=0 ; i<rc.size() ; i++ ) {
					rc.set( i, factor * rc.getValue(i) ) ;
				}
			}
			return rc ;
		}
		return rc ;
	}

	
	@Override
	public void process( DataElement dataElement )  {
		super.process( dataElement );
		DataElement dataElementNew = calculate(dataElement) ;
		if( dataElementNew != null ) {
			super.process( dataElementNew );
		}
	}
}



