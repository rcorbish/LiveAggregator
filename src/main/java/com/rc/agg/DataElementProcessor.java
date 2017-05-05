package com.rc.agg;

import com.rc.datamodel.DataElement;

/**
 * The start of it all - entrypoint into the aggregator from the generator side
 * 
 * To add/update items in the model this is the method to call.
 * 
 * @author richard
 *
 */
public interface DataElementProcessor {
	public void process( DataElement dataElement ) throws InterruptedException ;
}
