package tcp;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Vector;

import org.junit.Test;

import client.tcp.TCPClient;

public class TestIntegration {
	
	private static final String SERVICE_HOST = "localhost";
	private static final int SERVICE_PORT = 9083;
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
		{"reserveItinerary", 1, "custid", new Vector(Arrays.asList(new String[]{"55","66"})), "montreal", true, true},
		{"queryCustomerInfo", 1, "custid"},
		{"queryFlight", 1, 55},
		{"queryFlight", 1, 66},
		{"queryRooms", 1, "montreal"},
		{"queryCars", 1, "montreal"},
		{"deleteCustomer", 1, "custid"},
		{"queryCustomerInfo", 1, "custid"},
		{"queryFlight", 1, 55},
		{"queryFlight", 1, 66},
		{"queryRooms", 1, "montreal"},
		{"queryCars", 1, "montreal"},
		
		//failed itinerary
		{"newCustomer", 1},
		{"addFlight", 1, 55, 10, 10},
		{"queryFlight", 1, 55},
		{"reserveItinerary", 1, "custid", new Vector(Arrays.asList(new String[]{"55"})), "boston", true, true},
		{"queryFlight", 1, 55}
	};
	private static final Object[] RESULT_LIST = new Object[]
	{
		null, // test 1
		true,
		true,
		9,
		true,
		10,
		
		null, // test 2
		true,
		true,
		true,
		true,
		true,
		null, // silly to test querycustomer
		9,
		9,
		9,
		9,
		true,
		null,
		10,
		10,
		10,
		10,
		
		null, // test 3
		true,
		20, // adding 10 seats
		false,
		20
	};
	

	
	@Test
	/**
	 * Utility to automate manual testing and verification of many commands.
	 * @throws Exception
	 */
	public void testClientAPI() throws Exception {
		TCPClient client = new TCPClient(SERVICE_HOST, SERVICE_PORT);
		
		int custid = 0;
		for(int i=0; i<RESULT_LIST.length; i++){
			Object[] command = COMMAND_LIST[i];
			for(int j=1; j<COMMAND_LIST[i].length; j++){
				if(COMMAND_LIST[i][j].equals("custid")){
					command = Arrays.copyOf(COMMAND_LIST[i], COMMAND_LIST[i].length);
					command[j] = custid;
				}
			}
			Object response = client.send(command);
			if (command[0].equals("newCustomer"))
			{
				// save the response for later assertions
				custid = (int)response;
			}
			else if(command[0].equals("queryCustomerInfo")){
				// Pretty long string, seems unnecessary to test exact equivalence.
				continue;
			}
			else{
				assertEquals(RESULT_LIST[i], response);
			}
		}
		client.close();
	}

}
