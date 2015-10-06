package client.tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * Similar to WSClient, but for TCP connections. 
 */
public class TCPClient {
	
	private Socket serviceSocket;
	ObjectInputStream serverIn;
	ObjectOutputStream serverOut;
	
	public TCPClient(String serviceHost, int servicePort) throws Exception{
		serviceSocket = new Socket(serviceHost, servicePort);
		serverOut = new ObjectOutputStream(serviceSocket.getOutputStream());
		serverIn = new ObjectInputStream(serviceSocket.getInputStream());
	}
	
	/**
	 * Send a message to the middleware and receive a response.
	 * @param message variable list of args, with the first being the method to call on the server
	 * @return the result
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public Object send(Object... message) throws IOException, ClassNotFoundException{
		serverOut.writeObject(message);
		return serverIn.readObject();
	}
	
	/**
	 * Cleans up the socket and stream connections.
	 * @throws IOException
	 */
	public void close() throws IOException{
		serverIn.close();
		serverOut.close();
		serviceSocket.close();
	}
}


