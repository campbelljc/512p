package middle.tcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class ConnectionHandler implements Runnable {

	Socket clientSocket;
	
	private String flightRMHostname;
	private String carRMHostname;
	private String roomRMHostname;
	
	private int flightRMPort;
	private int carRMPort;
	private int roomRMPort;
	
	public ConnectionHandler(Socket clientSocket, String[] rmHostanmes, int[] rmPorts){
		this.clientSocket = clientSocket;
		
		flightRMHostname = rmHostanmes[0];
		flightRMPort = rmPorts[0];
		
		carRMHostname = rmHostanmes[1];
		carRMPort = rmPorts[1];
		
		roomRMHostname = rmHostanmes[2];
		roomRMPort = rmPorts[2];
	}
	
	@Override
	public void run() {
		try {
			Socket rm;
			
			BufferedReader clientInput = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			BufferedReader rmInput;
			
			PrintWriter clientOutput = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
			PrintWriter rmOutput;

			String rmInputLine;
			String clientInputLine = clientInput.readLine();
			clientSocket.close();
			
			System.out.println(clientInputLine);
			
			rm = new Socket(flightRMHostname, flightRMPort);
			rmInput = new BufferedReader(new InputStreamReader(rm.getInputStream()));
			rmOutput = new PrintWriter(new OutputStreamWriter(rm.getOutputStream()));
			
			rmOutput.println(clientInputLine);
			rm.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

}
