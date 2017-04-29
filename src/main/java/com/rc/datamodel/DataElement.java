package com.rc.datamodel;

import java.util.regex.Pattern;

/**
 * A DataElement represents an input to the aggregator. It may be the result of
 * a calculation for example that will be presented to the viewer.
 * 
 * One DataElement contains several DataElement values
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
public class DataElement {

	public static final char ROW_COL_SEPARATION_CHAR = '\f' ;
	public static final char SEPARATION_CHAR = '\t' ;
	// @TODO - is this thread safe? 
	public static final Pattern SEPARATION_CHAR_PATTERN = Pattern.compile( Pattern.quote( String.valueOf(SEPARATION_CHAR) ) ) ;
	
	private final String invariantKey ;			// a key for this update - used to identify replacements
	
	private final double values[] ;			// each value 
	
	/** The column names ( e.g. Currency ), <b>in same order</b> as the coreValues + perimiter values */
	private final String attributeNames[] ;	
	
	private final String[] coreValues ;			// the core labels
	private final String[] perimeterValues[] ;	// the perimiter labels

	/**
	 * Constructor takes the attribute names (label names), core labels and the invariant key
	 * for the element. This version of the constructor expects a single value (one perimiter
	 * value).
	 * 
	 * After construction: call set - to set the actual values
	 *  
	 * @see set
	 * 
	 * @param attributeNames
	 * @param coreValues
	 * @param invariantKey
	 */
	public DataElement(String attributeNames[], String coreValues[], String invariantKey ) {
		this( 1, attributeNames, coreValues, invariantKey ) ;
	}
	
	
	/**
	 * Constructor takes the attribute names (label names), core labels and the invariant key
	 * for the element. This variant, expects 'length'  perimiter values to be set.
	 * 
	 * After construction: call set - to set the actual values
	 *  
	 * @see set
	 * 
	 * @param length
	 * @param attributeNames
	 * @param coreValues
	 * @param invariantKey
	 */
	public DataElement(int length, String attributeNames[], String coreValues[], String invariantKey ) {
		this.invariantKey 	= invariantKey ;		
		this.attributeNames = attributeNames ;
		this.coreValues 	= coreValues ;
		values 				= new double[length] ;
		perimeterValues 	= new String[length][] ;
	}

	/**
	 * Set the first value in the DataElement. 
	 * 
	 * @param attributeValues
	 * @param value
	 */
	public void set( String attributeValues[], double value ) {
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
	public void set( int index, String perimeterValues[], double value ) {
		this.perimeterValues[index] = perimeterValues ;
		this.values[index] = value ;		
	}
	
	/**
	 * Negate each value in the DataElement. This can be used to remove a previous
	 * copy from totals by adding the negative value of the values to the previous total.  
	 * 
	 * The original data element is unchanged by this operation.
	 * 
	 * @return
	 */
	public DataElement negatedCopy() {
		DataElement rc = new DataElement(size(), attributeNames, coreValues, getInvariantKey() ) ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.set(i, perimeterValues[i], -values[i] );
		}
		return rc ;		
	}
	/**
	 * Return the primary key for this item
	 * 
	 * @return
	 */
	public String getInvariantKey() {
		return invariantKey ;
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
		int ix = 0 ;
		for( String a : attributeNames ) {
			if( a.equals( attributeName ) ) {
				return ix<coreValues.length ? coreValues[ix] : perimeterValues[index][ix-coreValues.length] ;
			}
			ix++ ;
		}
		return "-none-" ;
	}

	/**
	 * Get the core attribute label from the core attributes. For example
	 * ask for 'Currency' and get 'USD'
	 *  
	 * @param attributeName
	 * @return the label value for the attribute name or null if no attribute exists
	 */
	public String getCoreAttribute( String attributeName ) {
		for( int ix = 0 ; ix < coreValues.length ; ix++ ) {
			if( attributeNames[ix].equals( attributeName ) ) {
				return coreValues[ix] ;
			}			
		}
		return null ;
	}

	/**
	 * How many values exist in this DataElement. 
	 * 
	 * @return
	 */
	public int size() {
		return values.length ;
	}
	
	/**
	 * Return the value at the given index.
	 * @param index
	 * @return
	 */
	public double getValue( int index ) {
		return values[index] ;
	}
	
	/**
	 * This helper method is used to split a key into separate components. 
	 * This would be expected to be used for the  data element label
	 * components - whera cell label is indexed by muli-level keys
	 * 
	 * @param in the input key as a flat string
	 * @return the array of components 
	 */
	static public String[] splitComponents( String in ) {
		return DataElement.SEPARATION_CHAR_PATTERN.split( in ) ;
	}

	/**
	 * This is the counterpoint to splitComponents 
	 * @param in an array of Strings to merge into a label
	 * @return the flat label of merged components
	 */
	static public String mergeComponents( String in[] ) {		
		StringBuilder rc = new StringBuilder( in[0] ) ;
		for( int i=1 ; i<in.length ; i++ ) {
			rc.append( SEPARATION_CHAR ).append( in[i] ) ;
		}
		return rc.toString() ;
	}

}
