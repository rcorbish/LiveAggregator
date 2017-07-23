
package com.rc.dataview ;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;

public class TestDataElementDataView extends DataElementDataView {
	final static Logger logger = LoggerFactory.getLogger( TestDataElementDataView.class ) ;

    public TestDataElementDataView(DataElementStore des, ViewDefinition viewDefinition, String arg ) {
        super( des, viewDefinition ) ;
        logger.info( "Creating new class {} with arg {}", getClass().getCanonicalName(), arg ) ;
    }

    public void start() {
        super.start();
    }

    public void process( DataElement dataElement ) {
        super.process(dataElement);
    }
}