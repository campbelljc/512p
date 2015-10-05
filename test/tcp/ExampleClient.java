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
	private static final String[] RESULT_LIST = new String[]{
		// TODO
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
		for(int i=0; i<COMMAND_LIST.length; i++){
			assertEquals(RESULT_LIST[i], writeStdinReadStdout(COMMAND_LIST[i]));
		}
	}

}
