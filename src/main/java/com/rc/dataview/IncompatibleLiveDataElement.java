package com.rc.dataview;

public class IncompatibleLiveDataElement extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public IncompatibleLiveDataElement( String previousElement, String newElement ) {
		super( "Multiple live data elements associated with Data View (" + 
					previousElement + " & " + newElement + ")" ) ;
	}
}
