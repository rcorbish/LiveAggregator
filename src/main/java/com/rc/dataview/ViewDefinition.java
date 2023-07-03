package com.rc.dataview;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rc.datamodel.DataElement;

/**
 * The definition of a view read from a text (config) file. 
 * A view defines what can be looked at by a client, i.e. which labels 
 * are used for rows and columns, what filters are supported.
 * 
 * @author richard
 *
 */
public class ViewDefinition {
	
	final static Logger logger = LoggerFactory.getLogger( ViewDefinition.class ) ;

	private final String name ;
	private String description ;
	private Class<? extends DataElementDataView> implementingClass ;
	private String constructorArg ;

	public String getDescription() {
		return description==null ? name : description ;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	private String[] colGroups;
	private String[] rowGroups;
	private String[] hiddenAttributes;
	private String[] totalAttributes;
	private final Map<String,String> filters ;

	// this is a map keyed on attribute name
	// mapping to another map
	// key1 =>   ( name of field to remap )
	// 	key2 = attribute value to match
	// 	value2 = attribute name to change
	private final Map<String,Map<String,String>> setValues ;
	
	public String getName() {
		return name;
	}

	public String[] getColGroups() {
		return colGroups;
	}

	public String[] getRowGroups() {
		return rowGroups;
	}

	public String[] getHiddenAttributes() {
		return hiddenAttributes;
	}

	public String[] getTotalAttributes() {
		return totalAttributes;
	}

	public Map<String, String> getFilters() {
		return filters;
	}
	public Map<String,Map<String,String>> getSetValues() {
		return setValues;
	}

	public ViewDefinition( String name ) {
		this.name = name ;
		colGroups = new String[0] ;
		rowGroups = new String[0] ;
		hiddenAttributes = new String[0] ;
		totalAttributes = new String[0] ;
		filters = new HashMap<>() ;
		setValues = new HashMap<>() ;
	}
	
	public void setImplementingClassName( String implementingClassName ){
		try {
			Class<?> clazz =  Class.forName(implementingClassName) ;
			this.implementingClass = clazz.asSubclass(DataElementDataView.class) ;
		} catch( Exception e ) {
			logger.error( "Failed to load class in view definition", e );
			throw new RuntimeException(e) ;
		}
	}
	public void setConstructorArg( String constructorArg ){
		this.constructorArg = constructorArg ;
	}

	public Class<? extends DataElementDataView> getImplementingClass() {
		return implementingClass==null ? DataElementDataView.class : implementingClass ;
	}
	public String  getConstructorArg(){
		return constructorArg ;
	}

	public void addColGroup( String colGroup ) {
		colGroups = Arrays.copyOf( colGroups, colGroups.length+1 ) ;
		colGroups[colGroups.length-1] = colGroup ;
 	}
	
	public void addRowGroup( String rowGroup ) {
		rowGroups = Arrays.copyOf( rowGroups, rowGroups.length+1 ) ;
		rowGroups[rowGroups.length-1] = rowGroup ;
 	}
	
	public void addHiddenAttribute( String attributeName ) {
		hiddenAttributes = Arrays.copyOf( hiddenAttributes, hiddenAttributes.length+1 ) ;
		hiddenAttributes[hiddenAttributes.length-1] = attributeName ;
 	}
	
	public void addTotalAttribute( String attributeName ) {
		totalAttributes = Arrays.copyOf( totalAttributes, totalAttributes.length+1 ) ;
		totalAttributes[totalAttributes.length-1] = attributeName ;
 	}
	
	public void addFilter( String attribute, String value ) {
        filters.merge(attribute, value, (a, b) -> a + DataElement.SEPARATION_CHAR + b);
 	}
	
	public void addSetValue( String attributeName, String attributeValue, String whenAttributeValue ) {
        Map<String, String> currentMap = this.setValues.computeIfAbsent(attributeName, k -> new HashMap<>());
        currentMap.put( attributeValue, whenAttributeValue ) ;
 	}
	
	public boolean equals( Object o ) {
		return o instanceof ViewDefinition && ((ViewDefinition)o).name.equals( name ) ;
	}
	
	public int hashCode() {
		return name.hashCode() ;
	}
}
