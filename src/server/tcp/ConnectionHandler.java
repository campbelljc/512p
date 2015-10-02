package server.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class ConnectionHandler implements Runnable {

	Socket clientSocket;

	public ConnectionHandler(Socket clientSocket){
		this.clientSocket = clientSocket;
	}

	@Override
	public void run() {
		try {
			BufferedReader clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			PrintWriter clientOutput = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

			// get the clients request
			String clientInputString = clientInput.readLine();
			
			System.out.println("Request received: " + clientInputString);
			// TODO: fulfill the request and get an appropriate response
			
			// TODO: send a useful response instead of this garbage
			clientOutput.println("Request fulfilled.");
			
			// close the resources
			clientInput.close();
			clientOutput.close();
			clientSocket.close();
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
