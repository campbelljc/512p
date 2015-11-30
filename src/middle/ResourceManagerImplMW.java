// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middle;

import java.util.*;

import javax.jws.WebService;

import server.*;

import middle.CrashPoint;
import middle.ServerName;
import middle.Message;
import middle.MasterRecord.NamedMessage;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImplMW implements server.ws.ResourceManager
{    
    protected RMHashtable m_itemHT = new RMHashtable();
	
	MasterRecord record;
    	
	middle.CrashPoint crashPoint;
	boolean commitReply = true;
	
	WSClient flightClient;
	WSClient carClient;
	WSClient roomClient;
	
    protected TransactionManager txnMgr;

	enum DType {
		CUSTOMER,
		FLIGHT,
		ROOM,
		CAR
	};
	
	public ResourceManagerImplMW() {
		try {
			Context env = (Context) new InitialContext().lookup("java:comp/env");

			System.out.println("*** Setting up clients ***");
		 	flightClient = new middle.WSClient("rm", (String) env.lookup("flight-service-host"), (Integer) env.lookup("flight-service-port")); // name, host, port
		 	carClient = new middle.WSClient("rm", (String) env.lookup("car-service-host"), (Integer) env.lookup("car-service-port")); // name, host, port
		 	roomClient = new middle.WSClient("rm", (String) env.lookup("room-service-host"), (Integer) env.lookup("room-service-port")); // name, host, port
			
			System.out.println("Loading hashtable data.");
			m_itemHT = RMHashtable.load(ServerName.MW, true); // load last committed version of data.
			
			// check for master record
			record = MasterRecord.loadLog(ServerName.MW);
			if (!record.isEmpty()){
				recover();
			}
			
			txnMgr = new TransactionManager(new WSClient[] { flightClient, carClient, roomClient }, this);
		} catch(NamingException e) {
			System.out.println(e);
		}
	}
			

	
	@Override
	public boolean getDecision(int tid)
	{
		ArrayList<NamedMessage> entries = record.getEntriesForTxn(tid);
		NamedMessage lastMsg = entries.get(entries.size() - 1);
		if (lastMsg.msg == Message.TM_COMMITTED)
			return true;
		else return false;
	}
	
	private void recover()
	{ // check master record for any deviation from norm
		// same code as server rm. (TODO: copy here when done)
	}
	
    // Basic operations on RMItem //
    
    // Read a data item.
    private RMItem readData(int id, String key) {
    	return (RMItem) m_itemHT.get(key);
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
		Object curVal = m_itemHT.get(key);
    	m_itemHT.put(key, value);
		m_itemHT.save(ServerName.MW, false); // save dirty changes
    }
    
    // Remove the item out of storage.
    protected RMItem removeData(int id, String key) {
		Object curVal = m_itemHT.get(key);
		RMItem removed = (RMItem) m_itemHT.remove(key);
		m_itemHT.save(ServerName.MW, false); // save dirty changes
		return removed;
    }
    
    // Basic operations on ReservableItem //

	protected Customer getCustomer(int id, int customerId) {
        // Read customer object if it exists (and read lock it).
		if (!txnMgr.requestRead(id, DType.CUSTOMER))
			return null;
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::getCustomer(" + id + ", " + customerId + ") failed: customer doesn't exist.");
            return null;
        } 
		return cust;
	}
    
    // Flight operations //
    
    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    @Override
    public boolean addFlight(int id, int flightNumber, 
                             int numSeats, int flightPrice) {
		// start a client to talk to the Flight RM.
		int oldPrice = queryFlightPrice(id, flightNumber);
    	if (!txnMgr.requestWrite(id, DType.FLIGHT, !flightExists(id, flightNumber) ? () -> flightClient.proxy.deleteFlight(id, flightNumber) : () -> flightClient.proxy.addFlight(id, flightNumber, -numSeats, oldPrice)))
			return false;
		boolean ret = flightClient.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {
		int x = queryFlight(id, flightNumber);
		int p = queryFlightPrice(id, flightNumber);
    	if (!txnMgr.requestWrite(id, DType.FLIGHT, () -> flightClient.proxy.addFlight(id, flightNumber, x, p)))
			return false;
		boolean ret = flightClient.proxy.deleteFlight(id, flightNumber);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    // Returns the number of empty seats on this flight.
    @Override
    public int queryFlight(int id, int flightNumber) {
    	if (!txnMgr.requestRead(id, DType.FLIGHT))
			return -1;
        return flightClient.proxy.queryFlight(id, flightNumber);
    }

    // Returns price of this flight.
	@Override
    public int queryFlightPrice(int id, int flightNumber) {
    	if (!txnMgr.requestRead(id, DType.FLIGHT))
			return -1;
		return flightClient.proxy.queryFlightPrice(id, flightNumber);
    }
	
	@Override
	public boolean flightExists(int id, int flightNumber) {
		if (!txnMgr.requestRead(id, DType.FLIGHT))
			return false;
		return flightClient.proxy.flightExists(id, flightNumber);
	}

    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {
		int oldPrice = queryCarsPrice(id, location);
    	if (!txnMgr.requestWrite(id, DType.CAR, !carExists(id, location) ? () -> carClient.proxy.deleteCars(id, location) : () -> carClient.proxy.addCars(id, location, -numCars, oldPrice)))
			return false;
		boolean ret = carClient.proxy.addCars(id, location, numCars, carPrice);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    // Delete cars from a location.
    @Override
    public boolean deleteCars(int id, String location) {
		int x = queryCars(id, location);
		int p = queryCarsPrice(id, location);
    	if (!txnMgr.requestWrite(id, DType.CAR, () -> carClient.proxy.addCars(id, location, x, p)))
			return false;
		boolean ret = carClient.proxy.deleteCars(id, location);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    // Returns the number of cars available at a location.
    @Override
    public int queryCars(int id, String location) {
    	if (!txnMgr.requestRead(id, DType.CAR))
			return -1;
		return carClient.proxy.queryCars(id, location);
    }

    // Returns price of cars at this location.
    @Override
    public int queryCarsPrice(int id, String location) {
    	if (!txnMgr.requestRead(id, DType.CAR))
			return -1;
		return carClient.proxy.queryCarsPrice(id, location);
    }
	
	@Override
	public boolean carExists(int id, String location) {
		if (!txnMgr.requestRead(id, DType.CAR))
			return false;
		return carClient.proxy.carExists(id, location);
	}
    

    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		int oldPrice = queryRoomsPrice(id, location);
    	if (!txnMgr.requestWrite(id, DType.ROOM, !roomExists(id, location) ? () -> roomClient.proxy.deleteRooms(id, location) : () -> roomClient.proxy.addRooms(id, location, -numRooms, oldPrice)))
			return false;
		boolean ret = roomClient.proxy.addRooms(id, location, numRooms, roomPrice);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    // Delete rooms from a location.
    @Override
    public boolean deleteRooms(int id, String location) {
		int x = queryRooms(id, location);
		int p = queryRoomsPrice(id, location);
    	if (!txnMgr.requestWrite(id, DType.ROOM, () -> roomClient.proxy.addRooms(id, location, x, p)))
			return false;
		boolean ret = roomClient.proxy.deleteRooms(id, location);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    // Returns the number of rooms available at a location.
    @Override
    public int queryRooms(int id, String location) {
    	if (!txnMgr.requestRead(id, DType.ROOM))
			return -1;
		return roomClient.proxy.queryRooms(id, location);
    }
    
    // Returns room price at this location.
    @Override
    public int queryRoomsPrice(int id, String location) {
    	if (!txnMgr.requestRead(id, DType.ROOM))
			return -1;
		return roomClient.proxy.queryRoomsPrice(id, location);
    }
    
	@Override
	public boolean roomExists(int id, String location) {
		if (!txnMgr.requestRead(id, DType.ROOM))
			return false;
		return roomClient.proxy.roomExists(id, location);
	}


    // Customer operations //

    @Override
    public int newCustomer(int id) {
   	 	Trace.info("INFO: RM::newCustomer(" + id + ") called.");
        // Generate a globally unique Id for the new customer.
        int customerId = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf(Math.round(Math.random() * 100 + 1)));

		if (!txnMgr.requestWrite(id, DType.CUSTOMER, () -> deleteCustomer(id, customerId)))
			return -1;

        Customer cust = new Customer(customerId);
        writeData(id, cust.getKey(), cust);
        Trace.info("RM::newCustomer(" + id + ") OK: " + customerId);
        return customerId;
    }

    // This method makes testing easier.
    @Override
    public boolean newCustomerId(int id, int customerId) {
	//	synchronized(m_itemHT) {
       	 	Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
			if (!txnMgr.requestRead(id, DType.CUSTOMER))
				return false;
	        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
	        if (cust == null) {
				if (!txnMgr.requestWrite(id, DType.CUSTOMER, () -> deleteCustomer(id, customerId)))
					return false;
	            cust = new Customer(customerId);
	            writeData(id, cust.getKey(), cust);
	            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") OK.");
	            return true;
	        } else {
	            Trace.info("INFO: RM::newCustomer(" + id + ", " + 
	                    customerId + ") failed: customer already exists.");
	            return false;
	        }
	//	}
    }

    // Delete customer from the database. 
    @Override
    public boolean deleteCustomer(int id, int customerId) {
	//	synchronized(m_itemHT) {
       	 	Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") called.");
			if (!txnMgr.requestRead(id, DType.CUSTOMER))
				return false;
	        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
	        if (cust == null) {
	            Trace.warn("RM::deleteCustomer(" + id + ", " 
	                    + customerId + ") failed: customer doesn't exist.");
	            return false;
	        } else {            
	            // Increase the reserved numbers of all reservable items that 
	            // the customer reserved. 
	            RMHashtable reservationHT = cust.getReservations();
	            for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
	                String reservedKey = (String) (e.nextElement());
	                ReservedItem reservedItem = cust.getReservedItem(reservedKey);
	                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): " 
	                        + "deleting " + reservedItem.getCount() + " reservations "
	                        + "for item " + reservedItem.getKey());
			
					String key = reservedItem.getKey();
					if (key.contains("flight")) {
						if (!txnMgr.requestWrite(id, DType.FLIGHT, () -> reserveFlight(id, customerId, Integer.parseInt(reservedItem.getLocation()))))
							return false;
						flightClient.proxy.deleteReservationWithKey(id, key, reservedItem.getCount());
					}
					else if (key.contains("room")) {
						if (!txnMgr.requestWrite(id, DType.ROOM, () -> reserveRoom(id, customerId, reservedItem.getLocation())))
							return false;
						roomClient.proxy.deleteReservationWithKey(id, key, reservedItem.getCount());
					}
					else if (key.contains("car")) {
						if (!txnMgr.requestWrite(id, DType.CAR, () -> reserveCar(id, customerId, reservedItem.getLocation())))
							return false;
						carClient.proxy.deleteReservationWithKey(id, key, reservedItem.getCount());
					}

	      //          ReservableItem item = 
	        //                (ReservableItem) readData(id, reservedItem.getKey());
	          //      item.setReserved(item.getReserved() - reservedItem.getCount());
	            //    item.setCount(item.getCount() + reservedItem.getCount());
	                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
	                        + reservedItem.getKey());
	            }
	            // Remove the customer from the storage.
				if (!txnMgr.requestWrite(id, DType.CUSTOMER, () -> newCustomerId(id, customerId)))
					return false;
	            removeData(id, cust.getKey());
	            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
	            return true;
	        }
	//	}
    }
	
	@Override
	public void deleteReservationWithKey(int id, String key, int count) {
		Trace.info("Should not be called!");
	}

    // Return data structure containing customer reservation info. 
    // Returns null if the customer doesn't exist. 
    // Returns empty RMHashtable if customer exists but has no reservations.
    public RMHashtable getCustomerReservations(int id, int customerId) {
        Trace.info("RM::getCustomerReservations(" + id + ", " 
                + customerId + ") called.");
		if (!txnMgr.requestRead(id, DType.CUSTOMER))
			return null;
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.info("RM::getCustomerReservations(" + id + ", " 
                    + customerId + ") failed: customer doesn't exist.");
            return null;
        } else {
            return cust.getReservations();
        }
    }

    // Return a bill.
    @Override
    public String queryCustomerInfo(int id, int customerId) {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + ") called.");
		if (!txnMgr.requestRead(id, DType.CUSTOMER))
			return "Error...";
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", " 
                    + customerId + ") failed: customer doesn't exist.");
            // Returning an empty bill means that the customer doesn't exist.
            return "Does not exist.";
        } else {
            String s = cust.printBill();
            Trace.info("RM::queryCustomerInfo(" + id + ", " + customerId + "): \n");
            System.out.println(s);
            return s;
        }
    }

    // Add flight reservation to this customer.  
    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) {
	//	synchronized(m_itemHT) {
       	 	
  //      return reserveItem(id, customerId, 
    //            Flight.getKey(flightNumber), String.valueOf(flightNumber));
		
			String key = Flight.getKey(flightNumber);
			String location = String.valueOf(flightNumber);
		
	        Trace.info("RM::reserveFlight(" + id + ", " + customerId + ", " 
	                + key + ", " + flightNumber + ") called.");

			if (!txnMgr.requestRead(id, DType.CUSTOMER))
				return false;

	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			if (!txnMgr.requestWrite(id, DType.FLIGHT, ()->flightClient.proxy.deleteReservationWithKey(id, key, 1)))
				return false;
			boolean reserved = flightClient.proxy.reserveFlight(id, customerId, flightNumber);
			if (reserved)
			{
	//			if (!txnMgr.requestRead(id, DType.FLIGHT))
	//				return false;
				int price = flightClient.proxy.queryFlightPrice(id, flightNumber);
				
				Customer oldCust = new Customer(cust);
	            
				String s = cust.reserve(key, location, price);
				Trace.info(s);

				if (!txnMgr.requestWrite(id, DType.CUSTOMER, () -> writeData(id, oldCust.getKey(), oldCust)))
					return false;
	            writeData(id, cust.getKey(), cust);
	            Trace.warn("RM::reserveFlight(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
				return true;
			}
			else
			{
				txnMgr.removeLastUndoOp(id);
	            Trace.warn("RM::reserveFlight(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed.");
				return false;
			}
	//	}
    }

    // Add car reservation to this customer. 
    @Override
    public boolean reserveCar(int id, int customerId, String location) {
  //      return reserveItem(id, customerId, Car.getKey(location), location);
		
	//	synchronized(m_itemHT) {
	 	 	String key = Car.getKey(location);
		
	        Trace.info("RM::reserveCar(" + id + ", " + customerId + ", " 
	                + key + ", " + location + ") called.");

			if (!txnMgr.requestRead(id, DType.CUSTOMER))
				return false;
			
	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			if (!txnMgr.requestWrite(id, DType.CAR, ()->carClient.proxy.deleteReservationWithKey(id, key, 1)))
				return false;
			boolean reserved = carClient.proxy.reserveCar(id, customerId, location);
			if (reserved)
			{
	//			if (!txnMgr.requestRead(id, DType.CAR))
	//				return false;
				int price = carClient.proxy.queryCarsPrice(id, location);
				
				Customer oldCust = new Customer(cust);
	            
				String s = cust.reserve(key, location, price);
				Trace.info(s);

				if (!txnMgr.requestWrite(id, DType.CUSTOMER, () -> writeData(id, oldCust.getKey(), oldCust)))
					return false;
	            writeData(id, cust.getKey(), cust);
	            Trace.warn("RM::reserveCar(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
				return true;
			}
			else
			{
				txnMgr.removeLastUndoOp(id);
	            Trace.warn("RM::reserveCar(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed.");
				return false;
			}
	//	}
    }

    // Add room reservation to this customer. 
    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
	//	synchronized(m_itemHT) {
			String key = Room.getKey(location);
		
	        Trace.info("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                + key + ", " + location + ") called.");

			if (!txnMgr.requestRead(id, DType.CUSTOMER))
				return false;
			
	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			if (!txnMgr.requestWrite(id, DType.ROOM, ()->roomClient.proxy.deleteReservationWithKey(id, key, 1)))
				return false;
			boolean reserved = roomClient.proxy.reserveRoom(id, customerId, location);
			if (reserved)
			{
		//		if (!txnMgr.requestRead(id, DType.ROOM))
		//			return false;
				int price = roomClient.proxy.queryRoomsPrice(id, location);
				
				Customer oldCust = new Customer(cust);
	            
				String s = cust.reserve(key, location, price);
				Trace.info(s);

				if (!txnMgr.requestWrite(id, DType.CUSTOMER, () -> writeData(id, oldCust.getKey(), oldCust)))
					return false;
	            writeData(id, cust.getKey(), cust);
	            Trace.warn("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
				return true;
			}
			else
			{
				txnMgr.removeLastUndoOp(id);
	            Trace.warn("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed.");
				return false;
			}
	//	}
		
    //    return reserveItem(id, customerId, Room.getKey(location), location);
    }
    

    // Reserve an itinerary.
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
		//synchronized(m_itemHT) {
       	 	
			// check if everything is available first, so we don't have to rollback reservations.
			for (int count = 0; count < flightNumbers.size(); count ++)
			{
				int flightNumber = Integer.parseInt((String)(flightNumbers.get(count)));
			
				if (!txnMgr.requestRead(id, DType.FLIGHT))
					return false;
				if (queryFlight(id, flightNumber) == 0)
				{
					Trace.warn("No flights available with that flight number " + flightNumber);
					return false;
				}
			}
			if (car)
			{
				if (!txnMgr.requestRead(id, DType.CAR))
					return false;
				if (queryCars(id, location) == 0)
				{
					Trace.warn("No cars available with that location " + location);
					return false;
				}
			}
			if (room)
			{
				if (!txnMgr.requestRead(id, DType.ROOM))
					return false;
				if (queryRooms(id, location) == 0)
				{
					Trace.warn("No rooms available with that location " + location);
					return false;
				}
			}
			for (int count = 0; count < flightNumbers.size(); count ++)
			{
				int flightNumber = Integer.parseInt((String)(flightNumbers.get(count)));
				Trace.warn("Trying to reserve flight " + flightNumber + " with id: " + id);
		//		if (!txnMgr.requestWrite(id, DType.FLIGHT, ()->flightClient.proxy.deleteReservationWithKey(id, Flight.getKey(flightNumber), 1)))
		//			return false;
				if (!reserveFlight(id, customerId, flightNumber))
				{
		//			txnMgr.removeLastUndoOp(id);
					Trace.warn("Failed.");
					return false;
				}
			}
			if (car)
			{
				Trace.warn("Trying to reserve car with id: " + id + " and location: " + location);
		//		if (!txnMgr.requestWrite(id, DType.CAR, ()->carClient.proxy.deleteReservationWithKey(id, Car.getKey(location), 1)))
		//			return false;
				if (!reserveCar(id, customerId, location))
				{
					Trace.warn("Failed.");
		//			txnMgr.removeLastUndoOp(id);
					return false;
				}
//				txnMgr.operationDone();
			}
			if (room)
			{
				Trace.warn("Trying to reserve room with id: " + id + " and location: " + location);
//				if (!txnMgr.requestWrite(id, DType.ROOM, ()->roomClient.proxy.deleteReservationWithKey(id, Room.getKey(location), 1)))
//					return false;
				if (!reserveRoom(id, customerId, location))
				{
					Trace.warn("Failed.");
//					txnMgr.removeLastUndoOp(id);
					return false;
				}
			}
			Trace.warn("Itinerary reservation successful.");
		
			return true;
			//}
    }

	@Override
	public int start() {
		return txnMgr.start();
	}

	@Override
	public boolean commit(int tid) {
		// TODO - need to save customer data to disk using m_itemHT.save()... where to do it?
		return txnMgr.commit(tid);
	}

	public boolean commit2(int tid) { // Different from above.
		record.log(tid, Message.RM_RCV_COMMIT_REQUEST, ServerName.MW);
		m_itemHT.save(ServerName.MW, true); // save committed changes
		record.log(tid, Message.RM_COMMIT_SUCCESS, ServerName.MW);
		return true;
	}

	@Override
	public boolean abort(int tid) {
		return txnMgr.abort(tid);
	}
	
	public boolean abort2(int tid) {
		record.log(tid, Message.RM_RCV_ABORT_REQUEST, ServerName.MW);
		// TODO: Delete uncommitted version on disk? Is that necessary though?
		record.log(tid, Message.RM_COMMIT_ABORTED, ServerName.MW);
		return true;
	}

	@Override
	public boolean shutdown() {
		txnMgr.shutdown();
		flightClient.proxy.shutdown();
		carClient.proxy.shutdown();
		roomClient.proxy.shutdown();
		Thread t = new Thread(new Runnable(){
			@Override
			public void run() {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				System.exit(0);
			}
		});
		t.start();
		return true;
	}
	
	@Override
	public boolean checkTransaction(int tid)
	{
		return txnMgr.checkTransaction(tid);
	}
	
	@Override
	public void crashAtPoint(String which, middle.CrashPoint pt)
	{
		switch(which) {
			case "FLIGHT":
				flightClient.proxy.selfDestruct(pt);
				break;
			case "CAR":
				carClient.proxy.selfDestruct(pt);
				break;
			case "ROOM":
				roomClient.proxy.selfDestruct(pt);
				break;
			case "MW":
				selfDestruct(pt);
				break;
		}
	}
	
	@Override
	public void crash(String which)
	{
		crashAtPoint(which, middle.CrashPoint.IMMEDIATE);
	}
	
	@Override
	public void selfDestruct(middle.CrashPoint pt)
	{
		crashPoint = pt;
		if (crashPoint == middle.CrashPoint.IMMEDIATE)
			System.exit(0);
	}
		
	@Override
	public void checkForCrash(middle.CrashPoint pt)
	{
		if (crashPoint == pt)
		{ // crash now
			selfDestruct(middle.CrashPoint.IMMEDIATE);
		}
	}
	
	@Override
	public void setVoteReply2(boolean commit_)
	{
		commitReply = commit_;
	}
	
	@Override
	public void setVoteReply(String which, boolean commit_)
	{
		switch(which) {
			case "FLIGHT":
				flightClient.proxy.setVoteReply2(commit_);
				break;
			case "CAR":
				carClient.proxy.setVoteReply2(commit_);
				break;
			case "ROOM":
				roomClient.proxy.setVoteReply2(commit_);
				break;
			case "MW":
				setVoteReply2(commit_);
				break;
		}
	}

	@Override
	public boolean voteRequest(int tid) {
		return true;
	}

	@Override
	public ServerName getName() {
		return ServerName.MW;
	}
	
	@Override
	public void setName(ServerName sName_)
	{
		System.out.println("WARNING-DO NOT CALL AGAIN");
	}
}
