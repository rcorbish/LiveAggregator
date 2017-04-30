package com.rc.datamodel;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to map an attribute name to it's index in the 
 * label array in a DataElement.
 * 
 * @author richard
 * @see DataElement
 */
public class DataElementAttributes {
	/**
	 * The map keyed on attribute name to find an index value
	 */
	private final Map<String,Integer> attributeIndices ;
	
	/**
	 * Create the internal map of name -> index
	 * @param attributeNames
	 */
	public DataElementAttributes( final String attributeNames[] ) {
		attributeIndices = new HashMap<>( attributeNames.length ) ;
		for( int i=0 ; i<attributeNames.length ; i++ ) {
			attributeIndices.put( attributeNames[i], i ) ;
		}
	}
	/**
	 * Given an attribute name - find its index in the label collection
	 * This doesn't care about core or perimiter values
	 * 
	 * @param attributeName
	 * @return if not found return -1 otherwise the index
	 */
	public int getAttributeIndex( String attributeName ) {
		Integer ix = attributeIndices.get( attributeName ) ;
		return ix!=null ? ix.intValue() : -1 ;
	}
		
}
