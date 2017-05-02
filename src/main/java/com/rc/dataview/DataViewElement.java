package com.rc.dataview;


/**
 * This is the actual thing that is stored in the DataElementStore. It's a
 * piece of data with flags to indicate it's been changed (updated) or deleted (unused)
 * 
 * @author richard
 *
 */
public class DataViewElement {
	private float value = 0.0f ;
	private boolean updated = false ;		// has this been updated since last send?
	private boolean unused = false ;		// Mark this as potentially no longer used

	/**
	 * Adds a value to the cell in a view. If the value 
	 * is non-zero the cell is marked as updated. If the cell
	 * value is (very close to) zero it is marked as unused
	 * and will be removed from the view
	 * 
	 * @param value
	 */
	public synchronized void add( float value ) {
//		Be careful with this, the data is read and written by 2
//		separate threads - the view (to updated new elelemnts ) 
//		and the message sender (which may reset the updated & unused flags)
//		The order of processing is important to avoid thread issues
		this.value += value ;
		this.updated = true ;
		this.unused = (value == 0.f) ;
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
	
	/**
	 * Mark a cell as unused, causing it to be removed
	 * from the output during processing. 
	 */
	public void markUnused() {
		value=0.0f ;
		unused = true ;
	}
	/**
	 * Call this after notifying the client that the
	 * update has been sent to the client. 
	 */
	public void clearUpdatedFlag() {
		// possible race condition when this is cleared at the 
		// same time as the add() method is running. 
		
		// There's not a lot we can do to fix it w/o hugely
		// complex event processing which would be very expensive
		updated = false ;
	}
}