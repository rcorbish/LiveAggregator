package com.rc.agg.client;

import java.io.IOException;

public abstract class ClientCommandProcessorImpl implements ClientCommandProcessor {

	protected void send( CharSequence ... messages ) throws IOException, InterruptedException {
		StringBuilder msg = new StringBuilder() ;
		for( CharSequence message : messages ) {
			msg.append( message ).append( '\f' ) ;
		}
		transmit( msg );
	}


	@Override
	public void closeClient() {
		try {
			send( "CLOSE" ) ;
		} catch (Exception ignore) {
			// Not much we can do here if we're closed anyway
		}
	}

	@Override
	public boolean heartbeat() {
		try {
			send( "HEARTBEAT" ) ;
		} catch (Exception e) {
			return false ;
		}			
		return true ;
	}
	
	@Override
	public void defineGrid(String gridName, String columnLevels, String rowLevels, String description) {
		try {
			send( gridName, "DIM", columnLevels, rowLevels, description ) ;
		} catch (Exception e) {
			closeClient() ;
		}			
	}

	@Override
	public void initializationComplete(String gridName) {
		try {
			send( gridName, "RDY" ) ;
		} catch (Exception e) {
			closeClient() ;
		}			
	}


	@Override
	public void updateCell(String gridName, String columnKey, String rowKey, String data) throws ClientDisconnectedException {
		try {
			send( gridName, "UPD", columnKey, rowKey, data ) ;
		} catch (Exception e) {
			closeClient() ;
			throw new ClientDisconnectedException() ;
		}			
	}

	@Override
	public void deleteCell(String gridName, String columnKey, String rowKey) throws ClientDisconnectedException {
		try {
			send( gridName, "DEL", columnKey, rowKey ) ;
		} catch (Exception e) {
			closeClient() ;
			throw new ClientDisconnectedException() ;
		}			
	}

	@Override
	public void deleteRow(String gridName, String rowKey) throws ClientDisconnectedException {
		try {
			send( gridName, "DELR", rowKey ) ;
		} catch (Exception e) {
			closeClient() ;
			throw new ClientDisconnectedException() ;
		}			
	}

	@Override
	public void deleteCol(String gridName, String columnKey ) throws ClientDisconnectedException {
		try {
			send( gridName, "DELC", columnKey ) ;
		} catch (Exception e) {
			closeClient() ;
			throw new ClientDisconnectedException() ;
		}			
	}


	protected abstract void transmit( CharSequence message ) throws IOException, InterruptedException ;
	

}