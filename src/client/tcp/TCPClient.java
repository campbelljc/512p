package client.tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;


public class TCPClient {
	
	private Socket serviceSocket;
	ObjectInputStream serverIn;
	ObjectOutputStream serverOut;
	
	public TCPClient(String serviceName, String serviceHost, int servicePort) throws Exception{
		serviceSocket = new Socket(serviceHost, servicePort);
		serverOut = new ObjectOutputStream(serviceSocket.getOutputStream());
		serverIn = new ObjectInputStream(serviceSocket.getInputStream());
	}
	
	public Object send(Object... message) throws IOException, ClassNotFoundException{
		serverOut.writeObject(message);
		return serverIn.readObject();
	}
	
	public void close() throws IOException{
		serverIn.close();
		serverOut.close();
		serviceSocket.close();
	}
}


