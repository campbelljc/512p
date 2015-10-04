package server.tcp;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Arrays;

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
			while((clientInputObj = (Object[])clientInput.readObject()) != null){
				clientInputMethodName = (String) clientInputObj[0];
				
				Class<?> c = Class.forName("ResourceManager");
				Method method = c.getDeclaredMethod (clientInputMethodName);
				method.invoke (resourceManager, Arrays.copyOfRange(clientInputObj, 1, clientInputObj.length-1));
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
