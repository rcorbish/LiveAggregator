package com.rc.agg.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;

public class WebSocketServer extends ClientCommandProcessorImpl implements AutoCloseable, Runnable {

	private static Set<ClientCommandProcessor> activeClients = new HashSet<>();
	private static ClientManager clientManager ;

	public static void Listen( final int port, ClientManager clientManager ) {
		WebSocketServer.clientManager = clientManager ;

		Thread listener = new Thread( new Runnable() {
			public void run() {
				Thread.currentThread().setName( "WebSocket Server");
				try ( ServerSocket serverSocket = new ServerSocket(port); ) {
					for( ; ; ) {
						try {
							Socket clientSocket = serverSocket.accept();
							System.out.println( "Opened client connection to socket" ) ;
							WebSocketServer wss = new WebSocketServer( clientSocket ) ;
							wss.start() ;
							activeClients.add( wss ) ;
						} catch( Exception ohohChongo ) {

						}
					}
				} catch( Exception ohohChongo ) {

				}
			}
		} ) ;

		listener.start() ;
	}


	private Socket socket ;
	private InputStream input ;
	private OutputStream output ;
	private ClientProxy clientProxy ;
	private Thread socketListener ;
	private Thread messageSender ;
	private BlockingQueue<CharSequence> outboundMessages ;
	private String clientName ;
	private int numBytesReceived ;
	private int numBytesSent ;
	
	private WebSocketServer( Socket socket ) throws IOException {
		numBytesReceived = 0 ;
		numBytesSent = 0 ;
		outboundMessages = new ArrayBlockingQueue<>( 5_000 ) ;
		this.socket = socket ;
		clientName = socket.getInetAddress().getHostName();
		input = socket.getInputStream() ; 
		output = socket.getOutputStream() ;
	}

	@Override
	public void closeClient() {
		super.closeClient();
		clientManager.closeClient( clientProxy ); 
		close() ;
	}

	protected ClientEventProcessor getClientEventProcessor() { return clientProxy ; }

	protected void start() {
		socketListener = new Thread( this ) ;
		socketListener.start();

		messageSender = new Thread( new Runnable() {
			public void run() {
				messageSender.setName( "WebSocket Tx" ) ;
				CharSequence cs ;
				try {
					while( messageSender !=null && !messageSender.isInterrupted() ) {
						cs = outboundMessages.take() ;
						transmitImpl(cs);
					}
				} catch( InterruptedException iex ) {

				} catch (IOException e) {
					System.out.println(" Webserver terminated :" + e.getLocalizedMessage() ) ;				
				}
				messageSender = null ;				
			}
		} ) ;
		messageSender.start();
	}
	@Override
	public void close() {
		activeClients.remove( this ) ;		
		try {
			if( messageSender != null ) {
				messageSender.interrupt();
				messageSender = null ;
			}
			if( socketListener != null ) {
				socketListener.interrupt();
				socketListener = null ;
			}
			if( socket != null ) {
				socket.close();
				socket = null ;
			}
		} catch (Exception ignore ) {
			System.out.println( "Error closing socket" + ignore.getMessage() ) ;
		} 
	}

	@Override
	public void run() {

		try {
			initiateWebsocketProtocol();
			System.out.println( "Web Socket protocol acknowledge response completed." ) ;
			clientProxy = new ClientProxy(clientManager,this) ;
			clientManager.addClient(clientProxy);
			clientProxy.start();

			while( socketListener!=null && !socketListener.isInterrupted() ) {

				String raw = readMessage() ;
				String lines[] = raw.split( "\n" ) ;
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
							send( "RSP", request, response ); 
						}
					}
				}
			}
		} catch (IOException e) {
			System.out.println( "Web Socket protocol failure - closing client." + e.getMessage() ) ;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println( "Listener interrupted - shutting down server Listener" ) ;
		} finally {
			if( clientProxy != null ) {
				clientProxy.close();
			}
			System.out.println( "Web Socket protocol shutdown completed." ) ;

		}
	}


	/**
 				// What a wonderful protocol :o
				// 1st byte is 129 - for a text message
				// 2nd - 8th bytes may be the length ( depends on the length - see above for sending )

				// A useful reference ...
				// http://stackoverflow.com/questions/8125507/how-can-i-send-and-receive-websocket-messages-on-the-server-side
				//

	 * @return
	 * @throws IOException
	 */
	protected String readMessage() throws IOException {

		// Read the message type ( we only support single message text messages = 0b10000001 )
		int type = input.read();
		numBytesReceived++ ;
		if( type != 129 ) {
			throw new IOException("Unsupported server request =" + type	+ " closing." ) ;
		} 		
		int length = input.read()  ;	// must remove the encoding flag from the length 
		numBytesReceived++ ;
		if( (length & 0x80) == 0 )  {	
			throw new IOException("Unmasked server request =" + type	+ " closing." ) ;						
		}
		length &= (byte)0x7f ;			// Strip the mask
		if (length == 126 ) {           // Is this a 2 byte length?
			int b1 = input.read() ;		// Then read it
			int b2 = input.read() ;
			numBytesReceived +=2 ;
			length = (b1 << 8) + b2 ;
		} else if( length == 127) { 
			throw new IOException("This server only supports messages up to 65K in length ... closing." ) ;						
		}

		byte buffer[] = new byte[ length + 4 ] ;

		int n = input.read( buffer ) ;
		numBytesReceived += n ;

		if( n != length+4 ) {	// read mask as well - hence +4
			throw new IOException("Unexpected EOF. Read " + n + " but expecting " + length + " bytes. [" + new String( buffer, "UTF-8" ) + "]" ) ;
		}
		for( int i=4 ; i<buffer.length ; i++ ) {
			buffer[i] ^= buffer[ i % 4 ] ;
		}

		return new String( buffer, 4, length, "UTF-8" ) ;
	}

	@Override
	protected void transmit( CharSequence message ) throws InterruptedException {
		outboundMessages.put( message ) ;
	}

	private void transmitImpl( CharSequence message ) throws IOException {
		byte buffer[] = new byte[ 16 ] ;
		int ix = 0 ;
		buffer[ix++] = (byte)129 ; // Text message

		if( message.length() <= 125 ) {
			buffer[ix++] = (byte)message.length() ;
		} else if( message.length() <= 65535 ) {
			buffer[ix++] = (byte)126 ;
			buffer[ix++] = (byte)((message.length()>>8) & 255);
			buffer[ix++] = (byte) (message.length() & 255) ;
		} else {
			throw new IOException( "Cannot write >64K buffer in this server") ; 
//			buffer[ix++] = (byte)127 ;
//			buffer[ix++] = (byte)((message.length()>>56) & 255);
//			buffer[ix++] = (byte)((message.length()>>48) & 255);
//			buffer[ix++] = (byte)((message.length()>>40) & 255);
//			buffer[ix++] = (byte)((message.length()>>32) & 255);
//			buffer[ix++] = (byte)((message.length()>>24) & 255);
//			buffer[ix++] = (byte)((message.length()>>16) & 255);
//			buffer[ix++] = (byte)((message.length()>>8) & 255);
//			buffer[ix++] = (byte) (message.length() & 255) ;
		}
		output.write( buffer, 0, ix ); 
		output.write( message.toString().getBytes() ) ;
		output.flush();
		
		numBytesSent += ix + message.length() ;
	}


	protected void initiateWebsocketProtocol() throws IOException, NoSuchAlgorithmException {
		Pattern WebSocketKey = Pattern.compile("Sec-WebSocket-Key:\\s([\\S]*)" );

		StringBuilder buffer = new StringBuilder() ;

		byte in[] =  new byte[ 10000 ] ;
		/*int n = */input.read( in ) ;

		String data = new String( in, "UTF-8" ) ;
		data = data.replaceAll( "[\r\n]", " " ) ;

		if (!data.startsWith("GET ") ) {
			throw new UnsupportedOperationException( "Not a valid Websocket request!" ) ;
		}
		buffer.append( "HTTP/1.1 101 Switching Protocols" ) ;
		buffer.append( "\r\n" ) ;
		buffer.append( "Connection: Upgrade").append( "\r\n" ) ;
		buffer.append( "Upgrade: WebSocket").append( "\r\n" ) ;
		buffer.append( "Sec-WebSocket-Protocol: DataGrid").append( "\r\n" ) ;
		buffer.append( "Sec-WebSocket-Accept: " ) ;
		Matcher m = WebSocketKey.matcher( data ) ;	 
		if( !m.find() ) {
			throw new UnsupportedOperationException( "Missing Sec-WebSocket-Accept" ) ;
		}

		String key = m.group( 1 ) ;
		//key = "dGhlIHNhbXBsZSBub25jZQ==";  // sample test from RFC 6455

		MessageDigest sha1 = MessageDigest.getInstance("SHA-1") ;
		sha1.update( key.getBytes( ) ) ;
		sha1.update( "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(  ) ) ;
		buffer.append( new String( Base64.encodeBase64(sha1.digest())) ).append( "\r\n" ) ;
		buffer.append( "\r\n" ) ;
		//String s = new String( buffer.array(), 0, buffer.position() ) ;
		//System.out.println( "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=" ) ;
		//System.out.println( s ) ;

		output.write( buffer.toString().getBytes() ) ;
		output.flush();
	}

	public String toString() {
		return clientName + " rcvd:" + numBytesReceived + " bytes  sent:" + numBytesSent + " bytes";
	}
}
