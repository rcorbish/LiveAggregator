package com.rc.dataview;


/**
 * This is the actual thing that is stored in the DataElementStore. It's a
 * piece of data with flags to indicate it's been changed (updated) or deleted (unused)
 * 
 * @author richard
 *
 */
public class DataViewElement {
	private double value = 0.0 ;
	private final boolean hidden ;
	private final boolean isTotal ;		// is this a special total element ?
	private boolean updated = false ;		// has this been updated since last send?
	private boolean unused = false ;		// Mark this as potentially no longer used


	/** 
	 * create a new element in the view. It must be known at 
	 * construction time that the element is hidden.
	 * 
	 * @param hidden, whether this element will ever be displayed on screen
	 */
	public DataViewElement( boolean hidden ) {
		this( hidden, false ) ;
	}

	/** 
	 * create a new element in the view. It must be known at 
	 * construction time that the element is hidden.
	 * 
	 * @param hidden, whether this element will ever be displayed on screen
	 * @param isTotal whether this represents a total cell on screen
	 */
	public DataViewElement( boolean hidden, boolean isTotal ) {
		this.hidden = hidden ;
		this.isTotal = isTotal ;
	}

	/**
	 * Adds a value to the cell in a view. If the value 
	 * is non-zero the cell is marked as updated. 
	 * 
	 * @param value
	 */
	public void add( double value ) {
//		Be careful with this, the data is read and written by 2
//		separate threads - the view (to updated new elements ) 
//		and the message sender (which may reset the updated & unused flags)
//		The order of processing is important to avoid thread issues
		this.value += value ;
		this.updated = true ;
		this.unused = false ; //(value == 0.f) ;
	}

	
	public void set( double value ) {
		this.updated = this.value != value ;		
		this.value = value ;
	}
	
	public double getValue() {
		return value ;
	}

	public boolean isUnused() {
		return unused ;
	}
	public boolean isUpdated() {
		return updated ;
	}
	public boolean isHidden() {
		return hidden ;
	}
	public boolean isTotal() {
		return isTotal ;
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