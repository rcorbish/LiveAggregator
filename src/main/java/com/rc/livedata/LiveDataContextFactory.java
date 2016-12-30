package com.rc.livedata;

public class LiveDataContextFactory {
	private static LiveDataContext instance = new LiveDataContext( "GLOBAL" )  ;
	
	public static LiveDataContext getInstance( String contextName ) {
		return instance ;
	}
}
