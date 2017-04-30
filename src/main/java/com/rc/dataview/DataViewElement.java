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
	private float value = 0.0f ;
	private boolean updated = false ;		// has this been updated since last send?
	private boolean unused = false ;		// Mark this as potentially no longer used

	/**
	 * Be careful with this, the data is read and written by 2
	 * separate threads - the view (to updated new elelemnts ) 
	 * and the message sender (which may reset the updated & unused flags)
	 * The order of processing is important to avoid thread issues
	 * @param value
	 */
	public synchronized void add( float value ) {
		this.value += value ;
		if(value != 0.0f) {
			this.updated = true ;
		}
		if( this.value<1e-4f && this.value>-1e-4f ) {
			markUnused();
		} else {
			this.unused = false ;
		}
	}

	public float getValue() {
		return value ;
	}

	public boolean isUnused() {
		return unused ;
	}
	public boolean isUpdated() {
		return updated ;
	}
	// This should only be called at the start of a batch - we reset the totals 
	public void markUnused() {
		value=0.0f ;
		unused = true ;
		updated = true ;
	}
	
	public void clearUpdatedFlag() {
		updated = false ;
	}
}