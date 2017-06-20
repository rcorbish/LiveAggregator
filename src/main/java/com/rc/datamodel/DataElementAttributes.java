package com.rc.datamodel;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is used to map an attribute name to its index in the 
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
	private final int numCoreAttributes ;
	private final String attributeNames[] ;
	private final long  attributeNamesHash ;

	/**
	 * Create the internal map of name -> index
	 * @param attributeNames
	 */
	public DataElementAttributes( final String attributeNames[], int numCoreAttributes ) {
		this.attributeNames = attributeNames ;
		this.numCoreAttributes = numCoreAttributes ;
		attributeIndices = new HashMap<>( attributeNames.length ) ;
		for( int i=0 ; i<attributeNames.length ; i++ ) {
			attributeIndices.put( attributeNames[i], i ) ;
		}
		long attributeNamesHash = 0L ;
		for( String s : attributeNames ) {
			attributeNamesHash <<= 8 ;
			attributeNamesHash ^= s.hashCode() ;
		}
		this.attributeNamesHash = attributeNamesHash ;
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
	
	/**
	 * Test for whether an attribute is a core attribute (once per element )
	 * or a perimeter element (repeated per value within an element)
	 * 
	 * @param attributeName
	 * @return whether this is a core attribute 
	 */
	public boolean isCoreAttributeName( String attributeName ) {
		return getAttributeIndex( attributeName ) < numCoreAttributes ;
	}
	/**
	 * This returns the set of attribute names, in  the same 
	 * order as defined by the constructor.
	 * @return the attribute names as String array
	 */
	public String[] getAttributeNames() {
		return attributeNames ;
	}

	public long getAttributeNameHash() {
		return attributeNamesHash ;
	}
}
