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
    	
	WSClient flightClient;
	WSClient carClient;
	WSClient roomClient;
	
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
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Write a data item.
    private void writeData(int id, String key, RMItem value) {
        synchronized(m_itemHT) {
            m_itemHT.put(key, value);
        }
    }
    
    // Remove the item out of storage.
    protected RMItem removeData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.remove(key);
        }
    }
    
    
    // Basic operations on ReservableItem //
    
    // Delete the entire item.
    protected boolean deleteItem(int id, String key) {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        // Check if there is such an item in the storage.
        if (curObj == null) {
            Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed: " 
                    + " item doesn't exist.");
            return false;
        } else {
            if (curObj.getReserved() == 0) {
                removeData(id, curObj.getKey());
                Trace.info("RM::deleteItem(" + id + ", " + key + ") OK.");
                return true;
            }
            else {
                Trace.info("RM::deleteItem(" + id + ", " + key + ") failed: "
                        + "some customers have reserved it.");
                return false;
            }
        }
    }
    
    // Query the number of available seats/rooms/cars.
    protected int queryNum(int id, String key) {
        Trace.info("RM::queryNum(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0;  
        if (curObj != null) {
            value = curObj.getCount();
        }
        Trace.info("RM::queryNum(" + id + ", " + key + ") OK: " + value);
        return value;
    }    
    
    // Query the price of an item.
    protected int queryPrice(int id, String key) {
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0; 
        if (curObj != null) {
            value = curObj.getPrice();
        }
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") OK: $" + value);
        return value;
    }

	protected Customer getCustomer(int id, int customerId) {
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::getCustomer(" + id + ", " + customerId + ") failed: customer doesn't exist.");
            return null;
        } 
		return cust;
	}
    // Reserve an item.
   /* protected boolean reserveItem(int id, int customerId, 
                                  String key, String location) {
        Trace.info("RM::reserveItem(" + id + ", " + customerId + ", " 
                + key + ", " + location + ") called.");
        Customer cust = getCustomer(id, customerId);
		if (cust == null)
			return false;
        // Check if the item is available.
        ReservableItem item = (ReservableItem) readData(id, key);
        if (item == null) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                    + key + ", " + location + ") failed: item doesn't exist.");
            return false;
        } else if (item.getCount() == 0) {
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                    + key + ", " + location + ") failed: no more items.");
            return false;
        } else {
            // Do reservation.
            cust.reserve(key, location, item.getPrice());
            writeData(id, cust.getKey(), cust);
            
            // Decrease the number of available items in the storage.
            item.setCount(item.getCount() - 1);
            item.setReserved(item.getReserved() + 1);
            
            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
                    + key + ", " + location + ") OK.");
            return true;
        }
    }*/
    
    
    // Flight operations //
    
    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    @Override
    public boolean addFlight(int id, int flightNumber, 
                             int numSeats, int flightPrice) {
		// start a client to talk to the Flight RM.
        return flightClient.proxy.addFlight(id, flightNumber, numSeats, flightPrice);
    }

    @Override
    public boolean deleteFlight(int id, int flightNumber) {
        return flightClient.proxy.deleteFlight(id, flightNumber);
    }

    // Returns the number of empty seats on this flight.
    @Override
    public int queryFlight(int id, int flightNumber) {
        return flightClient.proxy.queryFlight(id, flightNumber);
    }

    // Returns price of this flight.
	@Override
    public int queryFlightPrice(int id, int flightNumber) {
		return flightClient.proxy.queryFlightPrice(id, flightNumber);
    }

    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) {
		return carClient.proxy.addCars(id, location, numCars, carPrice);
    }

    // Delete cars from a location.
    @Override
    public boolean deleteCars(int id, String location) {
		return carClient.proxy.deleteCars(id, location);
    }

    // Returns the number of cars available at a location.
    @Override
    public int queryCars(int id, String location) {
		return carClient.proxy.queryCars(id, location);
    }

    // Returns price of cars at this location.
    @Override
    public int queryCarsPrice(int id, String location) {
		return carClient.proxy.queryCarsPrice(id, location);
    }
    

    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		return roomClient.proxy.addRooms(id, location, numRooms, roomPrice);
    }

    // Delete rooms from a location.
    @Override
    public boolean deleteRooms(int id, String location) {
		return roomClient.proxy.deleteRooms(id, location);
    }

    // Returns the number of rooms available at a location.
    @Override
    public int queryRooms(int id, String location) {
		return roomClient.proxy.queryRooms(id, location);
    }
    
    // Returns room price at this location.
    @Override
    public int queryRoomsPrice(int id, String location) {
		return roomClient.proxy.queryRoomsPrice(id, location);
    }


    // Customer operations //

    @Override
    public int newCustomer(int id) {
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

    // Delete customer from the database. 
    @Override
    public boolean deleteCustomer(int id, int customerId) {
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
                ReservableItem item = 
                        (ReservableItem) readData(id, reservedItem.getKey());
                item.setReserved(item.getReserved() - reservedItem.getCount());
                item.setCount(item.getCount() + reservedItem.getCount());
                Trace.info("RM::deleteCustomer(" + id + ", " + customerId + "): "
                        + reservedItem.getKey() + " reserved/available = " 
                        + item.getReserved() + "/" + item.getCount());
            }
            // Remove the customer from the storage.
            removeData(id, cust.getKey());
            Trace.info("RM::deleteCustomer(" + id + ", " + customerId + ") OK.");
            return true;
        }
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
		boolean reserved = flightClient.proxy.reserveFlight(id, customerId, flightNumber);
		if (reserved)
		{
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

    // Add car reservation to this customer. 
    @Override
    public boolean reserveCar(int id, int customerId, String location) {
  //      return reserveItem(id, customerId, Car.getKey(location), location);
		
		String key = Car.getKey(location);
		
        Trace.info("RM::reserveCar(" + id + ", " + customerId + ", " 
                + key + ", " + location + ") called.");
        Customer cust = getCustomer(id, customerId);
		if (cust == null)
			return false;
		
        // Check if the item is available.
		boolean reserved = carClient.proxy.reserveCar(id, customerId, location);
		if (reserved)
		{
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

    // Add room reservation to this customer. 
    @Override
    public boolean reserveRoom(int id, int customerId, String location) {
		String key = Room.getKey(location);
		
        Trace.info("RM::reserveRoom(" + id + ", " + customerId + ", " 
                + key + ", " + location + ") called.");
        Customer cust = getCustomer(id, customerId);
		if (cust == null)
			return false;
		
        // Check if the item is available.
		boolean reserved = roomClient.proxy.reserveRoom(id, customerId, location);
		if (reserved)
		{
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
		
		
    //    return reserveItem(id, customerId, Room.getKey(location), location);
    }
    

    // Reserve an itinerary.
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
        return false;
    }

}
