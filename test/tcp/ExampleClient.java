package tcp;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.junit.Test;

import client.tcp.Client;

public class ExampleClient {
	
	/**
	 * Runnable wrapper around client.tcp.Client.
	 */
	public class ClientRunner implements Runnable{
		Client client;
		
		public ClientRunner(String serviceName, String serviceHost, int servicePort) throws Exception {
			client = new Client(serviceName, serviceHost, servicePort);
		}
		
		@Override
		public void run() {
			client.run();
	        try {
				client.close();
			} catch (IOException e) {
				fail("Problem closing the client:" + e.getMessage());
			}
		}
		
	}
	
	private static final String SERVICE_NAME = "not_used";
	private static final String SERVICE_HOST = "localhost";
	private static final int SERVICE_PORT = 8080;
	private static final String[] COMMAND_LIST = new String[]{
		// delete customer
		"newcustomer, 1",
		"newroom, 1, room-1, 10, 10",
		"reserveroom, 1, custid, room-1",
		"queryroom, 1, room-1",
		"deletecustomer, 1, custid",
		"queryroom, 1, room-1",
		
		//itinerary
		"newcustomer, 1",
		"newflight, 1, 55, 10, 10",
		"newflight, 1, 66, 10, 10",
		"newroom, 1, montreal, 10, 10",
		"newcar, 1, montreal, 10, 10",
		"itinerary, 1, custid, 55, 66, montreal, true, true",
		"querycustomer, 1, custid",
		"queryflight, 1, 55",
		"queryflight, 1, 66",
		"queryroom, 1, montreal",
		"querycar, 1, montreal",
		"deletecustomer, 1, custid",
		"querycustomer, 1, custid",
		"queryflight, 1, 55",
		"queryflight, 1, 66",
		"queryroom, 1, montreal",
		"querycar, 1, montreal",
		
		//failed itinerary
		"newcustomer, 1",
		"newflight, 1, 55, 10, 10",
		"queryflight, 1, 55",
		"itinerary, 1, custid, 55, boston, true, true",
		"queryflight, 1, 55"
	};
	private static final String[] RESULT_LIST = new String[]
	{
		"*", // test 1
		"rooms added",
		"room Reserved",
		"number of rooms at this location: 9",
		"Customer Deleted",
		"number of rooms at this location: 10",
		"*", // test 2
		"Flight added",
		"Flight added",
		"rooms added",
		"cars added",
		"Itinerary Reserved",
		"*" // can't test querycustomer
		"Number of seats available: 9",
		"Number of seats available: 9",
		"number of rooms at this location: 9",
		"number of cars at this location: 9",
		"Customer Deleted",
		"Customer info:",
		"Number of seats available: 10",
		"Number of seats available: 10",
		"number of rooms at this location: 10",
		"number of cars at this location: 10",
		"*", // test 3
		"Flight added",
		"Number of seats available: 20", // adding 10 seats
		"Itinerary could not be reserved.",
		"Number of seats available: 20"
	};
	
	public static String writeStdinReadStdout(String input){
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		System.setOut(new PrintStream(out));
		System.setIn(new ByteArrayInputStream(input.getBytes()));
		return out.toString();
	}

	@Test
	public void testClientAPI() throws Exception {
		ClientRunner r = new ClientRunner(SERVICE_NAME, SERVICE_HOST, SERVICE_PORT);
		r.run();
		int id = 0;
		for(int i=0; i<COMMAND_LIST.length; i++){
			String command = COMMAND_LIST[i];
			if (command.contains("custid"))
			{
				command = command.replaceAll("custid", Integer.toString(id));
			}
			String response = writeStdinReadStdout(command);
			if (COMMAND_LIST[i].contains("newcustomer"))
			{
				id = Integer.parseInt(response[i].split(": ")[1]);
			}
			if (RESULT_LIST[i] != "*")
				assertEquals(RESULT_LIST[i], response);
		}
	}

}
