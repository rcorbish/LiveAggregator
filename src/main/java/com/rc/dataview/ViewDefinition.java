package com.rc.dataview;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.rc.datamodel.DataElement;


public class ViewDefinition {
	
	private String name ;
	private String description ;
	public String getDescription() {
		return description==null ? name : description ;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	private String colGroups[] ;
	private String rowGroups[] ;
	private Map<String,String> filters ;
	
	public String getName() {
		return name;
	}

	public String[] getColGroups() {
		return colGroups;
	}

	public String[] getRowGroups() {
		return rowGroups;
	}

	public Map<String, String> getFilters() {
		return filters;
	}

	public ViewDefinition( String name ) {
		this.name = name ;
		colGroups = new String[0] ;
		rowGroups = new String[0] ;
		filters = new HashMap<>() ;
	}
	
	public void addColGroup( String colGroup ) {
		colGroups = Arrays.copyOf( colGroups, colGroups.length+1 ) ;
		colGroups[colGroups.length-1] = colGroup ;
 	}
	
	public void addRowGroup( String rowGroup ) {
		rowGroups = Arrays.copyOf( rowGroups, rowGroups.length+1 ) ;
		rowGroups[rowGroups.length-1] = rowGroup ;
 	}
	
	public void addFilter( String attribute, String value ) {
		String currentFilter = filters.get( attribute ) ;
		if( currentFilter==null ) {
			filters.put( attribute, value) ;
		} else {
			filters.put( attribute, currentFilter + DataElement.SEPARATION_CHAR + value) ;
		}
 	}
	
	public boolean equals( Object o ) {
		return o instanceof ViewDefinition && ((ViewDefinition)o).name.equals( name ) ;
	}
	
	public int hashCode() {
		return name.hashCode() ;
	}

}
