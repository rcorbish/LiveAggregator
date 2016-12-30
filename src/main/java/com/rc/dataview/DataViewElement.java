package com.rc.dataview;



public class DataViewElement {
	double value = 0.0 ;
	boolean updated = false ;		// has this been updated since last send?
	boolean unused = false ;		// Mark this as potentially no longer used

	public synchronized void add( double value ) {
		this.value += value ;
		updated |= value != 0.0 ;
		unused = this.value == 0.0 ;
	}

	public double getValue() {
		return value ;
	}

	public boolean isUnused() {
		return unused ;
	}
	// This should only be called at the start of a batch - we reset the totals 
	public void markUnused() {
		value=0.0 ;
		unused = true ;
	}
	
	public void clear() {
		updated = false ;
	}
}