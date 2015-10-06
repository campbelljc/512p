package server.tcp;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;

import server.Trace;

/**
 * Class for handling requests from the middleware in a new thread.
 */
public class ConnectionHandler implements Runnable {

	Socket clientSocket;

	private ResourceManager resourceManager;

	public ConnectionHandler(Socket clientSocket, ResourceManager resourceManager){
		this.clientSocket = clientSocket;
		this.resourceManager = resourceManager;
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
					// a case where exception-based flow control is necessary.
					break;
				}
				// trust that the method name is the first argument
				clientInputMethodName = (String) clientInputObj[0];
				Trace.info("Client request: " + clientInputMethodName);
				clientOutputResponse = resourceManager.invokeMethodByName(clientInputMethodName, Arrays.copyOfRange(clientInputObj, 1, clientInputObj.length));
				clientOutput.writeObject(clientOutputResponse);
			}
			
			// cleanup streams and sockets
			clientInput.close();
			clientOutput.close();
			clientSocket.close();

		} catch (Exception e) {
			Trace.error(e.getMessage());
		}

	}
	
}
