package com.rc.datamodel;




public class DataElement {

	private String invariantKey ;
	private final double values[] ;
	private final String attributeNames[] ;
	private String[] coreValues ;
	private String[] perimeterValues[] ;

	public DataElement(String attributeNames[], String coreValues[], String invariantKey ) {
		this( 1, attributeNames, coreValues, invariantKey ) ;
	}

	public DataElement(int length, String attributeNames[], String coreValues[], String invariantKey ) {
		this.invariantKey 	= invariantKey ;		
		this.attributeNames = attributeNames ;
		this.coreValues 	= coreValues ;
		values 				= new double[length] ;
		perimeterValues 	= new String[length][] ;
	}

	public void set( String attributeValues[], double value ) {
		set( 0, attributeValues, value ) ;
	}
	
	public void set( int index, String perimeterValues[], double value ) {
		this.perimeterValues[index] = perimeterValues ;
		this.values[index] = value ;		
	}
	
	public DataElement negatedCopy() {
		DataElement rc = new DataElement(size(), attributeNames, coreValues, getInvariantKey() ) ;
		for( int i=0 ; i<rc.size() ; i++ ) {
			rc.set(i, perimeterValues[i], -values[i] );
		}
		return rc ;		
	}
	
	public String getInvariantKey() {
		return invariantKey ;
	}

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

	public String getCoreAttribute( String attributeName ) {
		for( int ix = 0 ; ix < coreValues.length ; ix++ ) {
			if( attributeNames[ix].equals( attributeName ) ) {
				return coreValues[ix] ;
			}			
		}
		return null ;
	}

	public int size() {
		return values.length ;
	}
	
	public double getValue( int index ) {
		return values[index] ;
	}
}
