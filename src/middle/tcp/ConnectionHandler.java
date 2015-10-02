package middle.tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ConnectionHandler implements Runnable {
	
	private static final String FLIGHT_STRING = "Flight";
	private static final String ROOM_STRING = "Room";
	private static final String CAR_STRING = "Car";
	private enum ResourceManager {
		FLIGHT, ROOM, CAR;
	}

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
	
	
	public Object sendToRM(ResourceManager rm, Object... message) throws IOException, ClassNotFoundException{
		Socket rmSocket = null;
		switch(rm) {
		case FLIGHT:
			rmSocket = new Socket(flightRMHostname, flightRMPort);
		case ROOM:
			rmSocket = new Socket(roomRMHostname, roomRMPort);
		case CAR:
			rmSocket = new Socket(carRMHostname, carRMPort);
		}
		ObjectInputStream rmInput = new ObjectInputStream(rmSocket.getInputStream());
		ObjectOutputStream rmOutput = new ObjectOutputStream(rmSocket.getOutputStream());
		rmOutput.writeObject(message);
		Object rmInputObj = rmInput.readObject();
		rmInput.close();
		rmOutput.close();
		rmSocket.close();
		return rmInputObj;
	}

	@Override
	public void run() {
		try {
			// get thread ID for stamping messages
			String threadId = Long.toString(Thread.currentThread().getId());
			
			// get client in/out streams
			ObjectInputStream clientInput = new ObjectInputStream(clientSocket.getInputStream());
			Object[] clientInputObj;
			String clientInputMethodName;
			ObjectOutputStream clientOutput = new ObjectOutputStream(clientSocket.getOutputStream());
			Object clientOutputResponse = null;
			
			// keep talking to the client until they leave us
			while((clientInputObj = (Object[])clientInput.readObject()) != null){
				clientInputMethodName = (String) clientInputObj[1];
				System.out.println( threadId + "| Client request: " + clientInputMethodName);
				
				if(clientInputMethodName.contains(FLIGHT_STRING)){
					clientOutputResponse = sendToRM(ResourceManager.FLIGHT, clientInputObj);
				}
				else if(clientInputMethodName.contains(ROOM_STRING)){
					clientOutputResponse = sendToRM(ResourceManager.ROOM, clientInputObj);
				}
				else if(clientInputMethodName.contains(CAR_STRING)){
					clientOutputResponse = sendToRM(ResourceManager.CAR, clientInputObj);
				}
				else{
					//TODO: middleware work
				}
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
