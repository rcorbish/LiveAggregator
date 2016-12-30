package com.rc.dataview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class ViewDefinition {
	
	private String name ;
	private String description ;
	public String getDescription() {
		return description==null ? name : description ;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	private List<String> colGroups ;
	private List<String> rowGroups ;
	private Map<String,String> filters ;
	
	public String getName() {
		return name;
	}

	public List<String> getColGroups() {
		return colGroups;
	}

	public List<String> getRowGroups() {
		return rowGroups;
	}

	public Map<String, String> getFilters() {
		return filters;
	}

	public ViewDefinition( String name ) {
		this.name = name ;
		colGroups = new ArrayList<>() ;
		rowGroups = new ArrayList<>() ;
		filters = new HashMap<>() ;
	}
	
	public void addColGroup( String colGroup ) {
		colGroups.add(colGroup) ; 
 	}
	
	public void addRowGroup( String rowGroup ) {
		rowGroups.add(rowGroup) ; 
 	}
	
	public void addFilter( String attribute, String value ) {
		String currentFilter = filters.get( attribute ) ;
		if( currentFilter==null ) {
			filters.put( attribute, value) ;
		} else {
			filters.put( attribute, currentFilter+"\t"+value) ;
		}
 	}
	
	public boolean equals( Object o ) {
		return o instanceof ViewDefinition && ((ViewDefinition)o).name.equals( name ) ;
	}
	
	public int hashCode() {
		return name.hashCode() ;
	}

}
