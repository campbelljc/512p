package tcp;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
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
import client.tcp.TCPClient;

public class TestIntegration {
	
	private static final String SERVICE_HOST = "localhost";
	private static final int SERVICE_PORT = 8080;
	private static final Object[][] COMMAND_LIST = {
		// delete customer
		{"newCustomer",1},
		{"addRooms", 1, "room-1", 10, 10},
		{"reserveRoom", 1, "custid", "room-1"},
		{"queryRooms", 1, "room-1"},
		{"deleteCustomer", 1, "custid"},
		{"queryRooms", 1, "room-1"},
		
		//itinerary
		{"newCustomer", 1},
		{"addFlight", 1, 55, 10, 10},
		{"addFlight", 1, 66, 10, 10},
		{"addRooms", 1, "montreal", 10, 10},
		{"addCars", 1, "montreal", 10, 10},
		{"reserveItinerary", 1, "custid", 55, 66, "montreal", true, true},
		{"queryCustomer", 1, "custid"},
		{"queryFlight", 1, 55},
		{"queryFlight", 1, 66},
		{"queryRoom", 1, "montreal"},
		{"queryCar", 1, "montreal"},
		{"deleteCustomer", 1, "custid"},
		{"queryCustomer", 1, "custid"},
		{"queryFlight", 1, 55},
		{"queryFlight", 1, 66},
		{"queryRoom", 1, "montreal"},
		{"queryCar", 1, "montreal"},
		
		//failed itinerary
		{"newCustomer", 1},
		{"addFlight", 1, 55, 10, 10},
		{"queryFlight", 1, 55},
		{"reserveItinerary", 1, "custid", 55, "boston", true, true},
		{"queryFlight", 1, 55}
	};
	private static final Object[] RESULT_LIST = new Object[]
	{
		"custid", // test 1
		true,
		true,
		9,
		true,
		10,
		"custid", // test 2
		true,
		true,
		true,
		true,
		true,
		"*", // can't test querycustomer
		9,
		9,
		9,
		9,
		true,
		"Customer info:\n" +
		"Number of seats available: 10\n" +
		"Number of seats available: 10\n" +
		"number of rooms at this location: 10\n" +
		"number of cars at this location: 10\n",
		"*", // test 3
		true,
		20, // adding 10 seats
		false,
		20
	};
	


	@Test
	public void testClientAPI() throws Exception {
		TCPClient client = new TCPClient(SERVICE_HOST, SERVICE_PORT);
		
		
		for(int i=0; i<RESULT_LIST.length; i++){
			Object response = client.send(COMMAND_LIST[i]);
			int custid;
			if(RESULT_LIST[i].equals("custid")){
				custid = (int) response;
			}
			else{
				assertEquals(RESULT_LIST[i], response);
			}
		}
		client.close();
	}

}
