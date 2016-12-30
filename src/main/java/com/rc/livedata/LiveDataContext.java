package com.rc.livedata;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LiveDataContext {
	private final String contextName ;
	private final Map<String,LiveDataElement> liveDataElements ;
	
	public LiveDataContext( String contextName ) {
		this.contextName = contextName ;
		this.liveDataElements = new ConcurrentHashMap<>() ;
	}
	
	public String getContextName() {
		return contextName ;
	}
	
	public void add( LiveDataElement liveDataElement ) {
		liveDataElements.put( liveDataElement.getName(), liveDataElement ) ;
	}
	
	public void update( String name, double value ) {
		LiveDataElement lde = liveDataElements.get(name) ;
		if( lde != null ) {
			lde.setCurrentValue( value ); 
		}
	}
}
