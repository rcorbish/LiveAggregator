package com.rc.agg.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.rc.agg.DataElementProcessor;
import com.rc.dataview.DataElementDataView;
import com.rc.dataview.DataElementStore;

/**
 * This holds the collection of all open clients. It is the interface 
 * for the model (DataElementStore) to talk with clients. It is used
 * mostly for handling batching of updates.
 * 
 * @author richard
 *
 */
public class ClientManager implements AutoCloseable {

	private DataElementStore	dataElementStore ;
	private Set<ClientProxy>	activeClients ;
	private boolean				serverBatchComplete ;
	
	public ClientManager() {
		activeClients = new HashSet<>() ;
	}

	
	public void addClient( ClientProxy clientProxy ) {
		activeClients.add( clientProxy ) ;
	}
	
	protected void closeClient( ClientProxy clientProxy ) {
		if( !activeClients.remove(clientProxy) ) {
			System.out.println( "Error - client is not found, unable to close." ) ;
		} else {
			System.out.println( "Closed client. " + activeClients.size() + " clients remain." );
		}
	}

	public void requestAllData( DataElementProcessor processor ) {
		dataElementStore.getAllData( processor ) ;
	}
	
	
	@Override
	public void close() {
		for( ClientProxy cp : activeClients ) {
			cp.close();
		}
	}

	public boolean isServerBatchComplete() {
		return serverBatchComplete;
	}


	/**
	 * This is for monitoring only - not normally a public access
	 * @return
	 */
	public Collection<ClientProxy> getActiveClients() {
		return activeClients;
	}

	public void setDataElementStore(DataElementStore dataElementStore) {
		this.dataElementStore = dataElementStore;
	}

	public DataElementDataView getDataElementDataView( String name ) {
		return dataElementStore.getDataElementDataView( name ) ;
	}

	public Collection<String> getDataViewNames() {
		return dataElementStore.getDataViewNames() ;
	}

}
