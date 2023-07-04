package com.rc.agg.client;

/**
 * Used to parse the client messages into a POJO. 
 * All messages between server and client <b>must</b> be in this format.
 * 
 * There's a little problem with rowkeys that I need to fix - the START messages needs a 2D array :(
 * 
 * @author richard
 *
 */
public class ClientMessage {
	public String viewName ;
	public String command ;
	public String[] rowKeys;
	public String[] colKeys;
	public String description ;
	public int rate ;
}