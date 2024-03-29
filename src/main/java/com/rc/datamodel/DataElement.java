package com.rc.datamodel;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



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
 * Core values are common to all DataElement values in a DataElement, perimeter values
 * are specific to each DataElement value. 
 * 
 * The results of calculation data frequently follow this pattern, e.g. a core value
 * may be the calculation batch number, the calculation date or submitted_by. The
 * perimeter values may be sample_number, calculation time, etc.
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
public class DataElement implements Cloneable, Comparable<DataElement> {
	final static Logger logger = LoggerFactory.getLogger( DataElement.class ) ;

	public static final char ROW_COL_SEPARATION_CHAR = '\f' ;
	public static final char SEPARATION_CHAR = '\t' ;
	public static final String SEPARATION_STRING = String.valueOf(SEPARATION_CHAR) ;
	public static final String ROW_COL_SEPARATION_STRING = String.valueOf(ROW_COL_SEPARATION_CHAR) ;
	private static final Pattern SEPARATION_CHAR_PATTERN = Pattern.compile( Pattern.quote( String.valueOf(SEPARATION_CHAR) ) ) ;
	
	private final long createdTime ;			// timestamp of creation
	private final String invariantKey ;			// a key for this update - used to identify replacements
	
	private final double[] values;				// each value

	private int size;
	/**
	 * This is a helper to get the index of an attribute given its name
	 */
	private final DataElementAttributes attributes ;	
	
	//private final long bloomFilter ;

	private final String[] coreValues;			// the core labels
	private final String[][] perimeterValues;	// the perimeter labels

	/**
	 * Constructor takes the attribute names (label names), core labels and the invariant key
	 * for the element. This version of the constructor expects a single value (one perimeter
	 * value). 
	 * 
	 * After construction: call set - to set the actual values
	 *  
	 * @see #set(int, String[], double)
	 * 
	 * @param attributes how to get an attribute index from its name
	 * @param coreValues the core values - specified once per element
	 * @param invariantKey the special attribute - the unique ID of this item
	 */
	public DataElement(DataElementAttributes attributes, String[] coreValues, String invariantKey ) {
		this( 1, attributes, coreValues, invariantKey ) ;
	}
	
	
	/**
	 * Constructor takes the attribute names (label names), core labels and the invariant key
	 * for the element. This variant, expects 'length'  perimeter values to be set.
	 * 
	 * After construction: call set - to set the actual values
	 *  
	 * @see #set(int, String[], double)
	 * 
	 * @param length number of values in this element
	 * @param attributes how to get an attribute index from its name
	 * @param coreValues the core values - specified once per element
	 * @param invariantKey the special attribute - the unique ID of this item
	 */
	public DataElement(int length, DataElementAttributes attributes, String[] coreValues, String invariantKey ) {
		this.createdTime	= System.currentTimeMillis() ;
		this.invariantKey 	= invariantKey ;		
		this.attributes		= attributes ;
		this.coreValues 	= coreValues ;
		this.size			= length ;
		values 				= new double[size] ;
		perimeterValues 	= new String[size][] ;
	}

	/**
	 * Set the first value in the DataElement. 
	 * 
	 * @param attributeValues
	 * @param value
	 */
	public void set(String[] attributeValues, double value ) {
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
	public void set(int index, String[] perimeterValues, double value ) {
		this.perimeterValues[index] = perimeterValues ;
		this.values[index] = value ;		
	}
	
	
	/**
	 * Sets the nth value in the DataElement 
	 * 
	 * @param index the DataElement value in the DataElement
	 * @param value
	 */
	public void set( int index, double value ) {
		this.values[index] = value ;		
	}
	
	public void sanitize() {
		var indicesToRemove = new int[size()];
		var numIndicesToRemove = 0 ;
		for( int i=0 ; i<size() ; i++ ) {
			for( int j=i+1 ; j<size() ; j++ ) {
				if( Arrays.deepEquals(perimeterValues[i], perimeterValues[j])) {
					indicesToRemove[numIndicesToRemove] = i ;
					numIndicesToRemove++;
					break;
				}
			}
		}
		for( int ix=0 ; ix<numIndicesToRemove ; ix++ ) {
			int from = indicesToRemove[ix]-ix ;
			int copyCount = size() - from - 1 ;
			System.arraycopy(perimeterValues, from+1, perimeterValues, from, copyCount ) ;
			System.arraycopy(values, from+1, values, from, copyCount ) ;
		}
		this.size = size() - numIndicesToRemove ;
	}
	/**
	 * Return the primary key for this item
	 * 
	 * @return the unique ID used for this instance
	 */
	public String getInvariantKey() {
		return invariantKey ;
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
		String rc =  ix<0 ? attributeName : ix<coreValues.length ? coreValues[ix] : perimeterValues[index][ix-coreValues.length] ;
		return rc == null ? "-" : rc ;
	}

	

	/**
	 * How many values exist in this DataElement. 
	 * 
	 * @return the number of values in this instance
	 */
	public int size() {
		return size ;
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
		for( var entry : matchingTests.entrySet() ) {
			if( attributes.isCoreAttributeName(entry.getKey())) {
				matches = entry.getValue().contains(getAttribute(entry.getKey()));
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
	public boolean matchesPerimeterKeys(int index, Map<String,Set<String>> matchingTests ) {
		boolean matches = true ;
		for( var entry : matchingTests.entrySet() ) {
			if( !attributes.isCoreAttributeName(entry.getKey())) {
				matches = entry.getValue().contains(getAttribute(index, entry.getKey()));
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
	 * Get a copy of the core values. 
	 * 
	 * @return a copy of the core values
	 */
	public String[] getCoreValues() {
		return Arrays.copyOf( this.coreValues, this.coreValues.length ) ;
	}

	public String[] getAttributeValues( int index ) {
		String[] rc = new String[ attributes.getAttributeNames().length ] ;
        System.arraycopy(coreValues, 0, rc, 0, coreValues.length);
        System.arraycopy(perimeterValues[index], 0, rc, coreValues.length, perimeterValues[index].length);
		return rc ;
	}
	/**
	 * Return the value at the given index.
	 * @param index
	 * @return the value for this element
	 */
	public double getValue( int index ) {
		return values[index] ;
	}

	public long getCreatedTime() {
		return createdTime ;
	}

	public int findIndex( DataElement other, int ix, String ... attributeNames ) {

		StringJoiner otherKey = new StringJoiner( SEPARATION_STRING ) ;
        for (String attributeName : attributeNames) {
            otherKey.add(other.getAttribute(ix, attributeName));
        }

		StringJoiner thisKey = new StringJoiner( SEPARATION_STRING ) ;

		for( int i=0 ; i<size() ; i++ ) {
			thisKey.setEmptyValue( getAttribute(i, attributeNames[0] )) ;
			for( int j=1 ; j<attributeNames.length ; j++ ) {
				thisKey.add( getAttribute(i, attributeNames[j] ) ) ;
			}
			if( otherKey.toString().equals( thisKey.toString() ) )  {
				return i ;
			}
		}
		return -1 ;
	}

	public DataElementAttributes getDataElementAttributes() {
		return attributes ;
	}
	/**
	 * This helper method is used to split a key into separate components. 
	 * This would be expected to be used for the  data element label
	 * components - where cell label is indexed by multi-level keys
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
	static public String mergeComponents( String ...in  ) {		
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
	 * Subtract the given element, value by value, from the receiver.
	 * This returns a new DataElement - DataElements are immutable
	 * 
	 * @param other the data element to subtract from 'this'
	 * @return a new copy of a data element.
	 */
	public DataElement subtract( DataElement other ) {
		
		DataElement rc;
		if( size() == other.size() && attributes.getAttributeNameHash()==other.attributes.getAttributeNameHash() ) {
			rc = new DataElement(size(), this.attributes, this.coreValues, getInvariantKey() ) ;
			for( int i=0 ; i<rc.size() ; i++ ) {
				rc.set( i, perimeterValues[i], values[i]-other.getValue(i) );
			}
		} else {
			logger.warn( "Slow subtract detected. Expect bad performance (if too many) - [ untested!!!! ]" ) ;
			//
			// NOT TESTED   NOT TESTED   NOT TESTED    NOT TESTED   NOT TESTED
			// 
			// if the core values are different - we can't subtract ( check that later )
			//
			String[] attributeNames = getAttributeNames() ;
			rc = new DataElement(size()+other.size(), this.attributes, this.coreValues, getInvariantKey() ) ;
			int[] flagsOther = new int[ size() ] ;
			int[] flagsThis = new int[ size() ] ;
			for( int i=0 ; i<size() ; i++ ) {		
				int matchingIndex = rc.findIndex(other, i, attributeNames) ;
				if( matchingIndex >= 0 ) {
					flagsOther[matchingIndex] = 1 ;
					flagsThis[i] = 1 ;
					rc.set( i, perimeterValues[i], values[i]-other.getValue(matchingIndex) );
				}
			}
			
			int numExtra = 0 ;
			for( int i=0 ; i<size() ; i++ ) {
				if( flagsThis[i] == 0 ) {
					rc.set( flagsThis[i], perimeterValues[i], getValue(flagsThis[i]) );
				}
				numExtra += flagsOther[i] ; 
			}
			
			if( numExtra > 0 ) {
				logger.info( "Extremely slow subtract detected. Try and subtract similar elements from each other." ) ;

				rc = rc.clone( size() + numExtra ) ;			
				for( int i=0 ; i<size() ; i++ ) {
					if( flagsOther[i] == 0 ) {
						numExtra-- ;
						rc.set( numExtra, other.perimeterValues[flagsOther[i]], -other.getValue(flagsOther[flagsOther[i]]) );
					}
				}
			}
		}
		return rc ;
	}
	

	/**
	 * Make an identical copy of the DataElement. This can be used to change a data Element
	 * for one view individually.  
	 * The original data element is unchanged by this operation.
	 * 
	 * @return a new DataElement with all values identical to the receiver
	 */
	public DataElement clone() {
		DataElement rc = new DataElement(size(), this.attributes, this.coreValues, getInvariantKey() ) ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.set(i, perimeterValues[i], values[i] );
		}
		return rc ;		
	}
	
	/**
	 * Make a copy of the DataElement, and sets a new invariantKey. This can be used to change a data Element
	 * for one view individually.  
	 * 
	 * The original data element is unchanged by this operation.
	 * 
	 * @see #clone(String)
	 * 
	 * @param invariantKey the new key to use for the cloned element
	 * @param coreValues core values to use
	 * @return a new DataElement with all values identical to the receiver
	 */
	public DataElement clone(String invariantKey, String[] coreValues) {
		
		DataElement rc = new DataElement(size(), this.attributes, coreValues, invariantKey ) ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.set(i, perimeterValues[i], values[i] );
		}
		return rc ;		
	}

	/**
	 * Make a copy of the DataElement, and sets a new invariantKey. This can be used to change a data Element
	 * for one view individually.  
	 * 
	 * The original data element is unchanged by this operation.
	 * 
	 * @see #clone(String, String[])
	 * 
	 * @param invariantKey the new key to use for the cloned element
	 * @return a new DataElement with all values identical to the receiver
	 */
	public DataElement clone( String invariantKey ) {
		return this.clone( invariantKey, this.coreValues ) ;
	}
	
	/**
	 * Make a copy of the DataElement, but add some extra space in the perimeter values. This copies
	 * the given n elements from the original. If n is smaller that the length - the first n items
	 * are copied. If n is larger - empty elements are appended to the receiver's copy
	 * 
	 * The original data element is unchanged by this operation.
	 * 
	 * @see #clone
	 * 
	 * @param newSize the size of the new perimeter values
	 * @return a new DataElement with all values identical to the receiver
	 */
	public DataElement clone( int newSize ) {
		DataElement rc = new DataElement( newSize, this.attributes, coreValues, invariantKey ) ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.set(i, perimeterValues[i], values[i] );
		}
		return rc ;		
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
	 * Creates a copy of an element, the copy contains only elements matching a certain
	 * attribute-value rule, in addition the value of the attribute is changed to a given
	 * value. This is used as part of the calculation code, which calculates new values
	 * from existing values. 
	 * 
	 * example:
	 * <pre>
	 * 
	 * 	input = { inv-key=6743, CCY=USD, DOB='12/25/2000', [ WEIGHT='170', value=55.0f ], [ WEIGHT='150', value=170.0f ] }
	 * 	clone( '6743-age', WEIGHT, '170', 'XL' )
	 * 	output = { inv-key=6743-age, DOB='12/25/2000', [ WEIGHT='XL', value=55.0f ] }
	 *  
	 * </pre>
	 * 
	 * The above output can be used to calculate a size from the weight as part of
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

		String[][] newPerimeterValues = new String[size()][] ;
		int[] valueIndices = new int[ size() ] ;
		
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

	@Override
	public int compareTo( DataElement o ) {
		long rc = o.createdTime - createdTime ;
		return rc<0 ? -1 : ( rc>0 ) ? 1 : 0  ;
	}
	public boolean equals( Object o ) {
		return o instanceof DataElement && 0 == compareTo((DataElement)o);
	}
	public int hashCode() {
		return invariantKey.hashCode();
	}

}
