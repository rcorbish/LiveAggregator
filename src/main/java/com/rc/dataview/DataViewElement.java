package com.rc.dataview;


/**
 * This is the actual thing that is stored in the DataElementStore. It's a
 * piece of data with flags to indicate it's been changed (updated) or deleted (unused)
 * The actual data being stored is a double. @TODO make it a float to save a bit of space
 * 
 * @author richard
 *
 */
public class DataViewElement {
	float value = 0.0f ;
	boolean updated = false ;		// has this been updated since last send?
	boolean unused = false ;		// Mark this as potentially no longer used

	public synchronized void add( float value ) {
		this.value += value ;
		updated |= value != 0.0f ;
		unused = this.value == 0.0f ;
	}

	public float getValue() {
		return value ;
	}

	public boolean isUnused() {
		return unused ;
	}
	// This should only be called at the start of a batch - we reset the totals 
	public void markUnused() {
		value=0.0f ;
		unused = true ;
	}
	
	public void clear() {
		updated = false ;
	}
}