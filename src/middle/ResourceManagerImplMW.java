// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middle;

import java.util.*;
import javax.jws.WebService;
import server.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

@WebService(endpointInterface = "server.ws.ResourceManager")
public class ResourceManagerImplMW implements server.ws.ResourceManager {
    
    protected RMHashtable m_itemHT = new RMHashtable();
    protected TransactionManager txnMgr = new TransactionManager();
    	
	WSClient flightClient;
	WSClient carClient;
	WSClient roomClient;
	
	enum DType {
		CUSTOMER,
		FLIGHT,
		HOTEL,
		CAR
	};
	
	public ResourceManagerImplMW() {
		try {
			Context env = (Context) new InitialContext().lookup("java:comp/env");

			System.out.println("*** Setting up clients ***");
		 	flightClient = new middle.WSClient("rm", (String) env.lookup("flight-service-host"), (Integer) env.lookup("flight-service-port")); // name, host, port
		 	carClient = new middle.WSClient("rm", (String) env.lookup("car-service-host"), (Integer) env.lookup("car-service-port")); // name, host, port
		 	roomClient = new middle.WSClient("rm", (String) env.lookup("room-service-host"), (Integer) env.lookup("room-service-port")); // name, host, port			
		} catch(NamingException e) {
			System.out.println(e);
		}
	}

    // Basic operations on RMItem //
    
    // Read a data item.
    private RMItem readData(int id, String key) {
    	txnMgr.requestRead(id, DType.CUSTOMER);
    	return (RMItem) m_itemHT.get(key);
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
    	txnMgr.requestWrite(id, DType.CUSTOMER, () -> m_itemHT.put(key, m_itemHT.get(key)));
    	m_itemHT.put(key, value);
    }
    
    // Remove the item out of storage.
    protected RMItem removeData(int id, String key) {
    	txnMgr.requestWrite(id, DType.CUSTOMER, () -> m_itemHT.put(key, m_itemHT.get(key)));
    	return (RMItem) m_itemHT.remove(key);
    }
    
    
    // Basic operations on ReservableItem //

	protected Customer getCustomer(int id, int customerId) {
        // Read customer object if it exists (and read lock it).		
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
    	txnMgr.requestWrite(id, DType.FLIGHT, flightExists(id, flightNumber) ? () -> flightClient.proxy.deleteFlight(id, flightNumber) : () -> flightClient.proxy.addFlight(id, flightNumber, -numSeats, -flightPrice));
		boolean ret = flightClient.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
		if (!ret)
		{
			txnMgr.removeLastUndoOp(id);
		}
		return ret;
    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {
    	txnMgr.requestWrite(id, DType.FLIGHT, () -> flightClient.proxy.addFlight(id, flightNumber, queryFlight(id, flightNumber), queryFlightPrice(id, flightNumber)));
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
    	txnMgr.requestRead(id, DType.FLIGHT);
        return flightClient.proxy.queryFlight(id, flightNumber);
    }

    // Returns price of this flight.
	@Override
    public int queryFlightPrice(int id, int flightNumber) {
    	txnMgr.requestRead(id, DType.FLIGHT);
		return flightClient.proxy.queryFlightPrice(id, flightNumber);
    }
	
	@Override
	public boolean flightExists(int id, int flightNumber) {
		txnMgr.requestRead(id, DType.FLIGHT);
		return flightClient.proxy.flightExists(id, flightNumber);
	}

    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {
    	txnMgr.requestWrite(id, DType.CAR, carExists(id, location) ? () -> carClient.proxy.deleteCars(id, location) : () -> carClient.proxy.addCars(id, location, -numCars, -carPrice));
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
    	txnMgr.requestWrite(id, DType.CAR, () -> carClient.proxy.addCars(id, location, queryCars(id, location), queryCarsPrice(id, location)));
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
    	txnMgr.requestRead(id, DType.CAR);
		return carClient.proxy.queryCars(id, location);
    }

    // Returns price of cars at this location.
    @Override
    public int queryCarsPrice(int id, String location) {
    	txnMgr.requestRead(id, DType.CAR);
		return carClient.proxy.queryCarsPrice(id, location);
    }
	
	@Override
	public boolean carExists(int id, String location) {
		txnMgr.requestRead(id, DType.CAR);
		return carClient.proxy.carExists(id, location);
	}
    

    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
    	txnMgr.requestWrite(id, DType.HOTEL, roomExists(id, location) ? () -> roomClient.proxy.deleteRooms(id, location) : () -> roomClient.proxy.addRooms(id, location, -numRooms, -roomPrice));
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
    	txnMgr.requestWrite(id, DType.HOTEL, () -> roomClient.proxy.addRooms(id, location, queryRooms(id, location), queryRoomsPrice(id, location)));
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
    	txnMgr.requestRead(id, DType.HOTEL);
		return roomClient.proxy.queryRooms(id, location);
    }
    
    // Returns room price at this location.
    @Override
    public int queryRoomsPrice(int id, String location) {
    	txnMgr.requestRead(id, DType.HOTEL);
		return roomClient.proxy.queryRoomsPrice(id, location);
    }
    
	@Override
	public boolean roomExists(int id, String location) {
		txnMgr.requestRead(id, DType.HOTEL);
		return roomClient.proxy.roomExists(id, location);
	}


    // Customer operations //

    @Override
    public int newCustomer(int id) {
//    	txnMgr.requestWrite(id, DType.CUSTOMER, () -> deleteCustomer(id, customerId));
    	
   	 	Trace.info("INFO: RM::newCustomer(" + id + ") called.");
        // Generate a globally unique Id for the new customer.
        int customerId = Integer.parseInt(String.valueOf(id) +
                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                String.valueOf(Math.round(Math.random() * 100 + 1)));
        Customer cust = new Customer(customerId);
        writeData(id, cust.getKey(), cust);
        Trace.info("RM::newCustomer(" + id + ") OK: " + customerId);
        return customerId;
    }

    // This method makes testing easier.
    @Override
    public boolean newCustomerId(int id, int customerId) {
		synchronized(m_itemHT) {
       	 	Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") called.");
	        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
	        if (cust == null) {
	            cust = new Customer(customerId);
	            writeData(id, cust.getKey(), cust);
	            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerId + ") OK.");
	            return true;
	        } else {
	            Trace.info("INFO: RM::newCustomer(" + id + ", " + 
	                    customerId + ") failed: customer already exists.");
	            return false;
	        }
		}
    }

    // Delete customer from the database. 
    @Override
    public boolean deleteCustomer(int id, int customerId) {
		synchronized(m_itemHT) {
       	 	Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") called.");
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
						txnMgr.requestWrite(id, DType.FLIGHT, () -> reserveFlight(id, customerId, Integer.parseInt(reservedItem.getLocation())));
						flightClient.proxy.deleteReservationWithKey(id, key, reservedItem.getCount());
					}
					else if (key.contains("room")) {
						txnMgr.requestWrite(id, DType.HOTEL, () -> reserveRoom(id, customerId, reservedItem.getLocation()));
						roomClient.proxy.deleteReservationWithKey(id, key, reservedItem.getCount());
					}
					else if (key.contains("car")) {
						txnMgr.requestWrite(id, DType.CAR, () -> reserveCar(id, customerId, reservedItem.getLocation()));
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
	            removeData(id, cust.getKey());
	            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
	            return true;
	        }
		}
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
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", " 
                    + customerId + ") failed: customer doesn't exist.");
            // Returning an empty bill means that the customer doesn't exist.
            return "";
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
		synchronized(m_itemHT) {
       	 	
  //      return reserveItem(id, customerId, 
    //            Flight.getKey(flightNumber), String.valueOf(flightNumber));
		
			String key = Flight.getKey(flightNumber);
			String location = String.valueOf(flightNumber);
		
	        Trace.info("RM::reserveFlight(" + id + ", " + customerId + ", " 
	                + key + ", " + flightNumber + ") called.");
	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			txnMgr.requestWrite(id, DType.CAR, ()->carClient.proxy.deleteReservationWithKey(id, key, 1));
			boolean reserved = flightClient.proxy.reserveFlight(id, customerId, flightNumber);
			if (reserved)
			{
				txnMgr.requestRead(id, DType.FLIGHT);
				int price = flightClient.proxy.queryFlightPrice(id, flightNumber);
	            cust.reserve(key, location, price);
	            writeData(id, cust.getKey(), cust);
	            Trace.warn("RM::reserveFlight(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
				return true;
			}
			else
			{
	            Trace.warn("RM::reserveFlight(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed.");
				return false;
			}
		}
    }

    // Add car reservation to this customer. 
    @Override
    public boolean reserveCar(int id, int customerId, String location) {
  //      return reserveItem(id, customerId, Car.getKey(location), location);
		
		synchronized(m_itemHT) {
	 	 	String key = Car.getKey(location);
		
	        Trace.info("RM::reserveCar(" + id + ", " + customerId + ", " 
	                + key + ", " + location + ") called.");
	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			txnMgr.requestWrite(id, DType.CAR, ()->carClient.proxy.deleteReservationWithKey(id, key, 1));
			boolean reserved = carClient.proxy.reserveCar(id, customerId, location);
			if (reserved)
			{
				txnMgr.requestRead(id, DType.CAR);
				int price = carClient.proxy.queryCarsPrice(id, location);
	            Trace.info(cust.reserve(key, location, price));
	            writeData(id, cust.getKey(), cust);
	            Trace.warn("RM::reserveCar(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
				return true;
			}
			else
			{
	            Trace.warn("RM::reserveCar(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed.");
				return false;
			}
		}
    }

    // Add room reservation to this customer. 
    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
		synchronized(m_itemHT) {
			String key = Room.getKey(location);
		
	        Trace.info("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                + key + ", " + location + ") called.");
	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			txnMgr.requestWrite(id, DType.HOTEL, ()->roomClient.proxy.deleteReservationWithKey(id, key, 1));
			boolean reserved = roomClient.proxy.reserveRoom(id, customerId, location);
			if (reserved)
			{
				txnMgr.requestRead(id, DType.HOTEL);
				int price = roomClient.proxy.queryRoomsPrice(id, location);
	            cust.reserve(key, location, price);
	            writeData(id, cust.getKey(), cust);
	            Trace.warn("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
				return true;
			}
			else
			{
	            Trace.warn("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed.");
				return false;
			}
		}
		
    //    return reserveItem(id, customerId, Room.getKey(location), location);
    }
    

    // Reserve an itinerary.
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
		synchronized(m_itemHT) {
       	 	
			// check if everything is available first, so we don't have to rollback reservations.
			for (int count = 0; count < flightNumbers.size(); count ++)
			{
				int flightNumber = Integer.parseInt((String)(flightNumbers.get(count)));
			
				txnMgr.requestRead(id, DType.FLIGHT);
				if (queryFlight(id, flightNumber) == 0)
				{
					Trace.warn("No flights available with that flight number " + flightNumber);
					return false;
				}
			}
			if (car)
			{
				txnMgr.requestRead(id, DType.CAR);
				if (queryCars(id, location) == 0)
				{
					Trace.warn("No cars available with that location " + location);
					return false;
				}
			}
			if (room)
			{
				txnMgr.requestRead(id, DType.HOTEL);
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
				txnMgr.requestWrite(id, DType.FLIGHT, ()->flightClient.proxy.deleteReservationWithKey(id, Flight.getKey(flightNumber), 1));
				if (!reserveFlight(id, customerId, flightNumber))
				{
					txnMgr.removeLastUndoOp(id);
					Trace.warn("Failed.");
					return false;
				}
				
				
			}
			if (car)
			{
				Trace.warn("Trying to reserve car with id: " + id + " and location: " + location);
				txnMgr.requestWrite(id, DType.CAR, ()->carClient.proxy.deleteReservationWithKey(id, Car.getKey(location), 1));
				if (!reserveCar(id, customerId, location))
				{
					Trace.warn("Failed.");
					txnMgr.removeLastUndoOp(id);
					return false;
				}
//				txnMgr.operationDone();
			}
			if (room)
			{
				Trace.warn("Trying to reserve room with id: " + id + " and location: " + location);
				txnMgr.requestWrite(id, DType.HOTEL, ()->roomClient.proxy.deleteReservationWithKey(id, Room.getKey(location), 1));
				if (!reserveRoom(id, customerId, location))
				{
					Trace.warn("Failed.");
					txnMgr.removeLastUndoOp(id);
					return false;
				}
			}
			Trace.warn("Itinerary reservation successful.");
		
			return true;
		}
    }

}
