package server.tcp;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;

import server.Trace;

public class ConnectionHandler implements Runnable {

	Socket clientSocket;

	private ResourceManager resourceManager;
	private Method[] resourceManagerMethods;

	public ConnectionHandler(Socket clientSocket, ResourceManager resourceManager){
		this.clientSocket = clientSocket;
		this.resourceManager = resourceManager;
		try {
			Class<?> c = Class.forName("server.tcp.ResourceManager");
			resourceManagerMethods = c.getDeclaredMethods();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}	
	}

	@Override
	public void run() {
		try {

			// get client in/out streams
			ObjectInputStream clientInput = new ObjectInputStream(clientSocket.getInputStream());
			Object[] clientInputObj;
			String clientInputMethodName;
			ObjectOutputStream clientOutput = new ObjectOutputStream(clientSocket.getOutputStream());
			Object clientOutputResponse = null;

			// keep talking to the client until they leave us
			while(true){
				try{
					clientInputObj = (Object[])clientInput.readObject();
				} catch(EOFException e){
					// a rare case where exception-based flow control is necessary.
					break;
				}
				clientInputMethodName = (String) clientInputObj[0];
				Trace.info("Client request: " + clientInputMethodName);
				clientOutputResponse = resourceManager.invokeMethodByName(clientInputMethodName, Arrays.copyOfRange(clientInputObj, 1, clientInputObj.length-1));
				clientOutput.writeObject(clientOutputResponse);
			}
			clientInput.close();
			clientOutput.close();
			clientSocket.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
}
