package middle.tcp;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ConnectionHandler implements Runnable {
	
	private static final String FLIGHT_STRING = "Flight";
	private static final String ROOM_STRING = "Room";
	private static final String CAR_STRING = "Car";
	private enum ResourceManagerType {
		FLIGHT, ROOM, CAR;
	}

	Socket clientSocket;
	
	private String flightRMHostname;
	private String carRMHostname;
	private String roomRMHostname;
	
	private int flightRMPort;
	private int carRMPort;
	private int roomRMPort;
	
	privaet ResourceManager manager;
		
	public ConnectionHandler(Socket clientSocket, String[] rmHostanmes, int[] rmPorts, ResourceManager manager){
		this.clientSocket = clientSocket;
		this.manager = manager;
		
		flightRMHostname = rmHostanmes[0];
		flightRMPort = rmPorts[0];
		
		carRMHostname = rmHostanmes[1];
		carRMPort = rmPorts[1];
		
		roomRMHostname = rmHostanmes[2];
		roomRMPort = rmPorts[2];
	}
	
	Socket flightSocket;
	Socket carSocket;
	Socket roomSocket;
	
	public Object sendToRM(ResourceManagerType rm, Object... message) throws IOException, ClassNotFoundException{
		Socket rmSocket = null;
		switch(rm) {
			case FLIGHT:
			{
				if (flightSocket == NULL)
					flightSocket = new Socket(flightRMHostname, flightRMport);
				rmSocket = flightSocket;
				break;
			}
			case ROOM:
			{
				if (carSocket == NULL)
					carSocket = new Socket(roomRMHostname, roomRMPort);
				rmSocket = carSocket;
				break;
			}
			case CAR:
			{
				if (roomSocket == NULL)
					roomSocket = new Socket(carRMHostname, carRMPort);
				rmSocket = roomSocket;
				break;
			}
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
			
			manager.setConnectionHandler(this);
			
			// keep talking to the client until they leave us
			while((clientInputObj = (Object[])clientInput.readObject()) != null){
				clientInputMethodName = (String) clientInputObj[0];
				System.out.println( threadId + "| Client request: " + clientInputMethodName);
				
				if(clientInputMethodName.contains(FLIGHT_STRING) && !clientInputMethodName.contains("reserve")){
					clientOutputResponse = sendToRM(ResourceManagerType.FLIGHT, clientInputObj);
				}
				else if(clientInputMethodName.contains(ROOM_STRING) && !clientInputMethodName.contains("reserve")){
					clientOutputResponse = sendToRM(ResourceManagerType.ROOM, clientInputObj);
				}
				else if(clientInputMethodName.contains(CAR_STRING) && !clientInputMethodName.contains("reserve")){
					clientOutputResponse = sendToRM(ResourceManagerType.CAR, clientInputObj);
				}
				else{
					// parse and use manager...
					switch(clientInputMethodName)
					{
						case "newCustomer":
						{
							int id = (int)clientInputObj[1];
							clientOutputResponse = manager.newCustomer(id);
							break;
						}
						case "deleteCustomer":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							clientOutputResponse = manager.deleteCustomer(id, customer);
							break;
						}
						case "queryCustomerInfo":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							clientOutputResponse = manager.queryCustomerInfo(id, customer);
							break;
						}
						case "reserveItinerary":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							Vector flightNumbers = (Vector)clientInputObj[3];
							String location = (String)clientInputObj[4];
							boolean car = (boolean)clientInputObj[5];
							boolean room = (boolean)clientInputObj[6];
							clientOutputResponse = manager.reserveItinerary(id, customer, flightNumbers, location, car, room);
							break;
						}
						case "newCustomerId":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							clientOutputResponse = manager.newCustomerId(id, customer);
							break;
						}
						case "reserveRoom":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							String location = (String)clientInputObj[3];
							clientOutputResponse = manager.reserveRoom(id, customer, location);
							break;
						}
						case "reserveCar":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							String location = (String)clientInputObj[3];
							clientOutputResponse = manager.reserveCar(id, customer, location);
							break;
						}
						case "reserveFlight":
						{
							int id = (int)clientInputObj[1];
							int customer = (int)clientInputObj[2];
							int flightNumber = (int)clientInputObj[3];
							clientOutputResponse = manager.reserveRoom(id, customer, flightNumber);
							break;
						}
						default:
						{
							Trace.warn("Error: Unknown method name sent from client: " + clientInputMethodName);
							break;
						}
					}
					
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
