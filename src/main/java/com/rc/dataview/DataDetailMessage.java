package com.rc.dataview ;

import com.rc.datamodel.DataElement;

public class DataDetailMessage implements Comparable<DataDetailMessage> {
    final public float value ;
    final public long  createdTime ;
    final public String invariantKey ;
    final public String columns[] ;

    final static public String HEADER_INVARIANT_KEY = "** HDRS **" ;
    public DataDetailMessage( DataElement de, int index ) {
        this.value = de.getValue(index) ;
        this.createdTime = de.getCreatedTime() ;
        this.invariantKey = de.getInvariantKey() ;
        this.columns = de.getAttributeValues(index) ;
    }
    public DataDetailMessage( String attributeNames[] ) {
        this.columns = attributeNames ;
        this.createdTime = System.currentTimeMillis() ;
        this.invariantKey =  HEADER_INVARIANT_KEY ;
        this.value = 0.f ;
    }
    
	@Override
	public int compareTo( DataDetailMessage o ) {
		long rc = o.createdTime - createdTime ;
		return rc<0 ? -1 : ( rc>0 ) ? 1 : 0  ;
	}

}