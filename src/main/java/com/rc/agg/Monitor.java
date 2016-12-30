package com.rc.agg;

import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.simpleframework.http.Request;
import org.simpleframework.http.Response;
import org.simpleframework.http.core.Container;
import org.simpleframework.http.core.ContainerServer;
import org.simpleframework.transport.Server;
import org.simpleframework.transport.connect.Connection;
import org.simpleframework.transport.connect.SocketConnection;

import com.rc.agg.client.ClientManager;
import com.rc.agg.client.ClientProxy;
import com.rc.dataview.ClientDataView;

public class Monitor implements Container, AutoCloseable {
	Connection connection = null;
	ClientManager clientManager ;
	
	public void start() {
		try {
	      Server server = new ContainerServer(this);
	      connection = new SocketConnection(server);
	      SocketAddress address = new InetSocketAddress(8111);
	      
	      connection.connect(address);
	       
		} catch( Exception ohohChongo ) {
			
		}
	}
	
	@Override
	public void handle(Request req, Response rsp) {
		try {
			rsp.setContentType( "text/html" );
			PrintStream out = rsp.getPrintStream() ;
			String path = req.getPath().getName() ;
			if( path==null ) path="" ;
			if( path.equalsIgnoreCase("client")) {
				try ( FileReader fr = new FileReader("Client.html") ) {
					for( int c = fr.read(); c>0 ; c=fr.read() ) {
						out.append( (char)c ) ;
					}
				}
			} else {
				out.println( "<html>");
				out.print( "<h2>Active clients</h2>");
				for( ClientProxy cp : clientManager.getActiveClients()) {
					out.println( "<h3>" + cp.toString() + "</h3>" ) ;
					for( ClientDataView dedv : cp.getOpenDataGrids().values() ) {
						out.println( "<h5>" + dedv.toString() + "</h5>" ) ;
					}
				}
				
				out.println( "</html>");
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	@Override
	public void close() throws Exception {
		if( connection != null ) {
			connection.close(); 
		}
	}

	public void setDataGridManager(ClientManager clientManager) {
		this.clientManager = clientManager;
	}

}
