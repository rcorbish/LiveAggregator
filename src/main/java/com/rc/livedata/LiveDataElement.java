package com.rc.livedata;

import java.util.ArrayList;
import java.util.List;

public class LiveDataElement {
	private double currentValue ;
	private final double startValue ;
	private final String name ;
	private long lastUpdated ;
	private final List<LiveDataElementUpdated> listeners ;
	
	public LiveDataElement( String name, double startValue ) {
		this.startValue = startValue ;
		this.name = name ;
		this.listeners = new ArrayList<>() ;
	}
	public void setCurrentValue( double currentValue ) {
		this.currentValue = currentValue ;
		lastUpdated = System.nanoTime() ;
		for( LiveDataElementUpdated ldeu : listeners ) {
			ldeu.liveDataElementUpdated(name, getChange() );
		}
	}
	public String getName() {
		return name ;
	}
	public double getChange() {
		return currentValue - startValue ;
	}
}
