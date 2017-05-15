package com.rc.datamodel;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A DataElement represents an input to the aggregator. It may be the result of
 * a calculation for example that will be presented to the viewer.
 * 
 * One DataElement contains several DataElement values.
 * 
 * <b>The class is immutable</b>
 * 
 * A DataElement value consists of a single value. Each value is labelled by
 * text labels. Text labels come in two varieties: core values and perimeter values.
 * Core values are common to all DataElement values in a DataElement, permiter values 
 * are specific to each DataElement value. 
 * 
 * The results of calculation data frequently follow this pattern, e.g. a core value
 * may be the caluclation batch number, the calculation date or submitted_by. The 
 * perimiter values may be sample_number, cacluclation time, etc. 
 * 
 * When a replacement to a DataElement is given to the aggregator <b>all</b> values
 * and labels associated with the original are replaced. So to delete a previously
 * supplied  DataElement send in an empty DataElement with the same invariant key
 * 
 * A DataElement contains size() values, which can be accessed by getValue() and set() 
 * 
 * @author richard
 *
 */
public class DataElement  {

	public static final char ROW_COL_SEPARATION_CHAR = '\f' ;
	public static final char SEPARATION_CHAR = '\t' ;
	// @TODO - is this thread safe? 
	private static final Pattern SEPARATION_CHAR_PATTERN = Pattern.compile( Pattern.quote( String.valueOf(SEPARATION_CHAR) ) ) ;
	
	private final String invariantKey ;			// a key for this update - used to identify replacements
	
	private final float values[] ;				// each value 

	/**
	 * This is a helper to get the index of an attribute given its name
	 */
	private final DataElementAttributes attributes ;	
	
	private final String coreValues[] ;			// the core labels
	private final String perimeterValues[][] ;	// the perimiter labels

	/**
	 * Constructor takes the attribute names (label names), core labels and the invariant key
	 * for the element. This version of the constructor expects a single value (one perimiter
	 * value). 
	 * 
	 * After construction: call set - to set the actual values
	 *  
	 * @see #set(int, String[], float)
	 * 
	 * @param attributes how to get an attribute index from its name
	 * @param coreValues the corevalues - specified once per element
	 * @param invariantKey the special attribute - the unique ID of this item
	 */
	public DataElement(DataElementAttributes attributes, String coreValues[], String invariantKey ) {
		this( 1, attributes, coreValues, invariantKey ) ;
	}
	
	
	/**
	 * Constructor takes the attribute names (label names), core labels and the invariant key
	 * for the element. This variant, expects 'length'  perimiter values to be set.
	 * 
	 * After construction: call set - to set the actual values
	 *  
	 * @see #set(int, String[], float)
	 * 
	 * @param length number of values in this element
	 * @param attributes how to get an attribute index from its name
	 * @param coreValues the corevalues - specified once per element
	 * @param invariantKey the special attribute - the unique ID of this item
	 */
	public DataElement(int length, DataElementAttributes attributes, String coreValues[], String invariantKey ) {
		this.invariantKey 	= invariantKey ;		
		this.attributes		= attributes ;
		this.coreValues 	= coreValues ;
		values 				= new float[length] ;
		perimeterValues 	= new String[length][] ;
	}

	/**
	 * Set the first value in the DataElement. 
	 * 
	 * @param attributeValues
	 * @param value
	 */
	public void set( String attributeValues[], float value ) {
		set( 0, attributeValues, value ) ;
	}
	
	/**
	 * Sets the nth value in the DataElement along with all the labels.  
	 * For example, sets a value to 7.0 to labels 'Sample ID', 'pH', 'Machine number'
	 * 
	 * @param index the DataElement value in the DataElement
	 * @param perimeterValues
	 * @param value
	 */
	public void set( int index, String perimeterValues[], float value ) {
		this.perimeterValues[index] = perimeterValues ;
		this.values[index] = value ;		
	}
	
	
	/**
	 * Sets the nth value in the DataElement 
	 * 
	 * @param index the DataElement value in the DataElement
	 * @param value
	 */
	public void set( int index, float value ) {
		this.values[index] = value ;		
	}
	
	
	/**
	 * Negate each value in the DataElement. This can be used to remove a previous
	 * copy from totals by adding the negative value of the values to the previous total.  
	 * 
	 * The original data element is unchanged by this operation.
	 * 
	 * @return anew DataElement with all values having the opposite sign as the receiver
	 */
	public DataElement negatedCopy() {
		DataElement rc = new DataElement(size(), this.attributes, this.coreValues, getInvariantKey() ) ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.set(i, perimeterValues[i], -values[i] );
		}
		return rc ;		
	}
	/**
	 * Return the primary key for this item
	 * 
	 * @return the uniqueu ID used for this instance
	 */
	public String getInvariantKey() {
		return invariantKey ;
	}

	/**
	 * Return the value of the core attribute given the name of the attribute. 
	 * The attributeName will be found by searching the list of attributes 
	 * to find the index of the attribute.   
	 * 
	 * @param attributeName 
	 * @return the value of the given attribute key
	 */
	public String getAttribute( String attributeName ) {
		int ix = attributes.getAttributeIndex(attributeName) ;		
		return ix<0 ? null : ix<coreValues.length ? coreValues[ix] : null ;
	}

	/**
	 * Return the value of the attribute given the name of the attribute. 
	 * The attributeName will be found by searching the list of attributes 
	 * to find the index of the attribute.  
	 * 
	 * @param index the DataElement value in the DataElement
	 * @param attributeName 
	 * @return the value of the given attribute key
	 */
	public String getAttribute( int index, String attributeName ) {
		int ix = attributes.getAttributeIndex(attributeName) ;		
		return ix<0 ? attributeName : ix<coreValues.length ? coreValues[ix] : perimeterValues[index][ix-coreValues.length] ;
	}

	/**
	 * Find the index of the named attribute
	 * 
	 * @param attributeName
	 * @return the zero based index of the attribute
	 */
	public int getAttributeIndex( String attributeName ) {
		return attributes.getAttributeIndex(attributeName) ;
	}
	
	
	/**
	 * Get the core attribute label from the core attributes. For example
	 * ask for 'Currency' and get 'USD'
	 *  
	 * @param attributeName
	 * @return the label value for the attribute name or null if no attribute exists
	 * 
	 */
	public String getCoreAttribute( String attributeName ) {
		int ix = attributes.getAttributeIndex(attributeName) ;		
		return ix<0 ? attributeName : ix<coreValues.length ? coreValues[ix] : null ;
	}

	/**
	 * Return the value of the perimiter attribute given the name of the attribute. 
	 * The attributeName is used to find the index of the attribute.  
	 * 
	 * @param index the DataElement value in the DataElement
	 * @param attributeName 
	 * @return the value of the given attribute key or null if NOT a perimiter attribute
	 */
	public String getPerimiterAttribute( int index, String attributeName ) {
		int ix = attributes.getAttributeIndex(attributeName) ;		
		return ix>coreValues.length ? perimeterValues[index][ix-coreValues.length] : null ;
	}

	/**
	 * How many values exist in this DataElement. 
	 * 
	 * @return the number of values in this instance
	 */
	public int size() {
		return values.length ;
	}


		

	/**
	 * Identify whether the receiver's core element match any core
	 * keys in the match test. If no core keys are present, a match 
	 * is indicated.
	 * The test is provided as a Map, keyed on the attribute name 
	 * and an array of strings. One of the strings in that array must
	 * match the receiver's attribute to be deemed a match
	 * 
	 * @param matchingTests - the set of tests to do for this element 
	 * @return whether this matches the keys
	 */
	public boolean matchesCoreKeys( Map<String,Set<String>> matchingTests ) {
		boolean matches = true ;
		for( String attributeName : matchingTests.keySet() ) {
			if( attributes.isCoreAttributeName(attributeName)) {
				matches &= matchingTests.get( attributeName ).contains( getAttribute(attributeName)) ;
				if( !matches ) break ;
			}
 		}
		return matches ;
	}
	
	/**
	 * Identify whether the receiver's core element match any core
	 * keys in the match test. If no core keys are present, a match 
	 * is indicated.
	 * The test is provided as a Map, keyed on the attribute name 
	 * and an array of strings. One of the strings in that array must
	 * match the receiver's attribute to be deemed a match
	 * 
	 * @param index which of the set of perimeter attributes to examine
	 * @param matchingTests - the set of tests to do for this element 
	 * @return whether this matches the keys
	 */
	public boolean matchesPerimiterKeys( int index, Map<String,Set<String>> matchingTests ) {
		boolean matches = true ;
		for( String attributeName : matchingTests.keySet() ) {
			if( !attributes.isCoreAttributeName(attributeName)) {
				matches &= matchingTests.get( attributeName ).contains( getAttribute(index,attributeName)) ;
				if( !matches ) break ;
			}
 		}
		return matches ;
	}
	
	/**
	 * Get a list of attribute names.
	 * @return attributeNames - in the same order as originally defined.
	 */
	public String[] getAttributeNames() {
		return attributes.getAttributeNames() ;
	}
	/**
	 * Return the value at the given index.
	 * @param index
	 * @return the value for this element
	 */
	public float getValue( int index ) {
		return values[index] ;
	}
	
	public DataElementAttributes getDataElementAttributes() {
		return attributes ;
	}
	/**
	 * This helper method is used to split a key into separate components. 
	 * This would be expected to be used for the  data element label
	 * components - whera cell label is indexed by muli-level keys
	 * 
	 * @param in the input key as a flat string
	 * @return the array of components 
	 */
	static public synchronized String[] splitComponents( String in ) {
		return DataElement.SEPARATION_CHAR_PATTERN.split( in ) ;
	}

	/**
	 * This is the counterpoint to splitComponents 
	 * @param in an array of Strings to merge into a label
	 * @return the flat label of merged components
	 */
	static public String mergeComponents( String in[] ) {		
		if( in.length == 0 ) return "" ;
		StringBuilder rc = new StringBuilder( in[0] ) ;
		for( int i=1 ; i<in.length ; i++ ) {
			rc.append( SEPARATION_CHAR ).append( in[i] ) ;
		}
		return rc.toString() ;
	}

	public String toString() {
		return invariantKey + "=>" + values[0] ;
	}
	
	/**
	 * Creates a copy of an element, the copy containes only elements matching a certain 
	 * attribute-value rule. . In addition the value of the attribute is changed to a given
	 * value. This is used as part of the calculation code, which calculates new values
	 * from existing values. 
	 * 
	 * example:
	 * <pre>
	 * 
	 * 	input = { inv-key=6743, CCY=USD, DOB='12/25/2000', [ WEIGHT='170', value=100.0f ], [ WEIGHT='150', value=100.0f ] }
	 * 	clone( '6743-age', WEIGHT, '170', 'XL' )
	 * 	output = { inv-key=6743-age, DOB='12/25/2000', [ WEIGHT='XL', value=100.0f ] }
	 *  
	 * </pre>
	 * 
	 * The above output can be used to calculate an size from the weight as part of
	 * a calculating view. <need a better example>
	 *   
	 * @param invariantKey the unique ID of the key to find in the data store
	 * @param attributeName the name of the attribute category to match
	 * @param from the value of the attribute to match
	 * @param to the new value of the attribute in the returned element
	 * @return a new DataElement - if not matches are found this may be null 
	 * 
	 */
	public DataElement filteredClone( String invariantKey, String attributeName, String from, String to ) {

		String newPerimeterValues[][] = new String[size()][] ;
		int valueIndices[] = new int[ size() ] ;
		
		DataElement rc = null ;
		int ix = 0 ;
		for( int i=0 ; i<size() ; i++ ) {
			if( getAttribute(i, attributeName ).equals( from ) ) {
				newPerimeterValues[ix] = Arrays.copyOf( perimeterValues[i],perimeterValues[i].length ) ;
				newPerimeterValues[ix][0] = to ;
				valueIndices[ix] = i ;
				ix++ ;
			}
		}
		if( ix > 0 ) {
			rc = new DataElement(ix, this.attributes, this.coreValues, invariantKey ) ;
			for( int i=0 ; i<ix ; i++ ) {
				rc.set(i, newPerimeterValues[i], values[valueIndices[i]] );				
			}
		}
		return rc ;		
	}
}
