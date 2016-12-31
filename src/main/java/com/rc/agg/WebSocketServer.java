package com.rc.agg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import com.rc.agg.client.ClientCommandProcessorImpl;
import com.rc.agg.client.ClientManager;
import com.rc.agg.client.ClientProxy;


@WebSocket
public class WebSocketServer  {
	
	static ClientManager clientManager ;
	
    private static final Map<Session,ClientProxy> clientData = new ConcurrentHashMap<>() ;

	@OnWebSocketConnect
	public void connect( Session session )  {
		WebSocketCommandProcessor wscp = new WebSocketCommandProcessor(session) ;
		ClientProxy cp = new ClientProxy(clientManager, wscp ) ;
		clientData.put( session, cp ) ;
	}

	@OnWebSocketClose
	public void closeClient(Session session, int statusCode, String reason) {				
		ClientProxy cp = clientData.get( session ) ;
		if( cp != null ) {
			cp.close();
		}
		clientData.remove(session);
	}

	@OnWebSocketError
	public void error(Session session, Throwable error ) {
		error.printStackTrace(); 
		ClientProxy cp = clientData.get( session ) ;
		if( cp != null ) {
			cp.close();
		}
		clientData.remove(session);
	}


	@OnWebSocketMessage
	public void message(Session session, String message) throws IOException {

		ClientProxy clientProxy = clientData.get( session ) ;

		String lines[] = message.split( "\n" ) ;
		String components[] = lines[0].split( "\\f" ) ;

		if( components.length > 1 ) {
			String gridName = components[0] ;
			String request = components[1] ;

			if( request.equals("START") ) {
				List<String> openColKeys = new ArrayList<>() ;
				List<String> openRowKeys = new ArrayList<>() ;
				for( int i=1 ; i<lines.length ; i++ ) {
					if( lines[i].startsWith("EXC\f" ) ) {
						openColKeys.add( lines[i].substring(4) ) ;
					} else if( lines[i].startsWith("EXR\f" ) ) {
						openRowKeys.add( lines[i].substring(4) ) ;
					}
				}
				clientProxy.openGrid(gridName, openColKeys, openRowKeys);

			} else if( request.equals("STOP") ) {
				clientProxy.closeGrid(gridName);
			} else if( request.equals("EXR") && components.length>3 ) {
				String rowKey = components[2] ;
				boolean expanded = components[3].equals( "OPE" ) ;
				// Example View /f EXCO /f RowKey /f CLO  ( collapsed ) | OPE ( expanded )
				clientProxy.expandCollapseRow( gridName, rowKey, expanded ) ;
			} else if( request.equals("EXC") && components.length>3 ) {
				String colKey = components[2] ;
				boolean expanded = components[3].equals( "OPE" ) ;
				clientProxy.expandCollapseCol( gridName, colKey, expanded ) ;
			} else if( request.equals("RST")  ) {
				clientProxy.resetGrid(gridName) ;
			} else if( request.equals("RDY")  ) {
				clientProxy.gridReady(gridName) ;
			}

		} else {
			String request = lines[0] ;					
			if( request.equals("STOP") ) {
				clientProxy.close();
			} else {
				CharSequence response = clientProxy.respond( request ) ;
				if( response != null ) {	
					session.getRemote().sendString( "RSP" + "\f" + request + "\f" + response ); 
				}
			}
		}
	}

}


class WebSocketCommandProcessor extends ClientCommandProcessorImpl {
	private Session session ;
	
	public WebSocketCommandProcessor( Session session ) {
		this.session = session ;
	}
	
	@Override
	protected void transmit(CharSequence message) throws IOException, InterruptedException {		
		session.getRemote().sendString( message.toString() ); 
	}
}
