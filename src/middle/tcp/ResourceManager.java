// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package middle;

import java.util.*;
import javax.jws.WebService;
import server.*;

public class ResourceManager {
    
    protected RMHashtable m_itemHT = new RMHashtable(); // for customer data
	ConnectionHandler handler;
	
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
    
	protected Customer getCustomer(int id, int customerId) {
        // Read customer object if it exists (and read lock it).
        Customer cust = (Customer) readData(id, Customer.getKey(customerId));
        if (cust == null) {
            Trace.warn("RM::getCustomer(" + id + ", " + customerId + ") failed: customer doesn't exist.");
            return null;
        } 
		return cust;
	}
	
    public int newCustomer(int id) {
		synchronized(m_itemHT) {
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
    }
	
    // This method makes testing easier.
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
	
    // Returns the number of empty seats on this flight.
    public int queryFlight(int id, int flightNumber) {
		return handler.sendToRM(ResourceManagerType.FLIGHT, "queryFlight", id, flightNumber);
    }

    // Returns the number of cars available at a location.
    public int queryCars(int id, String location) {
		return handler.sendToRM(ResourceManagerType.CAR, "queryCars", id, location);
    }

    // Returns the number of rooms available at a location.
    public int queryRooms(int id, String location) {
		return handler.sendToRM(ResourceManagerType.ROOM, "queryRooms", id, location);
    }
    
	public void setConnectionHandler(ConnectionHandler handler)
	{
		this.hander = handler;
	}

    // Customer operations //

    // Delete customer from the database. 
    public boolean deleteCustomer(int id, int customerId, ) {
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
					if (key.contains("flight"))
						handler.sendToRM(ResourceManagerType.FLIGHT, "deleteReservationWithKey", id, key, reservedItem.getCount());
					else if (key.contains("room"))
						handler.sendToRM(ResourceManagerType.ROOM, "deleteReservationWithKey", id, key, reservedItem.getCount());
					else if (key.contains("car"))
						handler.sendToRM(ResourceManagerType.CAR, "deleteReservationWithKey", id, key, reservedItem.getCount());

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
			boolean reserved = handler.sendToRM(ResourceManagerType.FLIGHT, "reserveFlight", id, customerId, flightNumber);
			if (reserved)
			{
				int price = handler.sendToRM(ResourceManagerType.FLIGHT, "queryFlightPrice", id, flightNumber);
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
			boolean reserved = handler.sendToRM(ResourceManagerType.CAR, "reserveCar", id, customerId, location);
			if (reserved)
			{
				int price = handler.sendToRM(ResourceManagerType.CAR, "queryCarsPrice", id, location);
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
    public boolean reserveRoom(int id, int customerId, String location) {
		synchronized(m_itemHT) {
			String key = Room.getKey(location);
		
	        Trace.info("RM::reserveRoom(" + id + ", " + customerId + ", " 
	                + key + ", " + location + ") called.");
	        Customer cust = getCustomer(id, customerId);
			if (cust == null)
				return false;
		
	        // Check if the item is available.
			boolean reserved = handler.sendToRM(ResourceManagerType.ROOM, "reserveRoom", id, customerId, location);
			if (reserved)
			{
				int price = handler.sendToRM(ResourceManagerType.ROOM, "queryRoomsPrice", id, location);
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
    public synchronized boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
		synchronized(m_itemHT) {
       	 	
			// check if everything is available first, so we don't have to rollback reservations.
			for (int count = 0; count < flightNumbers.size(); count ++)
			{
				int flightNumber = Integer.parseInt((String)(flightNumbers.get(count)));
			
				if (queryFlight(id, flightNumber) == 0)
				{
					Trace.warn("No flights available with that flight number " + flightNumber);
					return false;
				}
			}
			if (car)
			{
				if (queryCars(id, location) == 0)
				{
					Trace.warn("No cars available with that location " + location);
					return false;
				}
			}
			if (room)
			{
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
				if (!reserveFlight(id, customerId, flightNumber))
				{
					Trace.warn("Failed.");
					return false;
				}
			}
			if (car)
			{
				Trace.warn("Trying to reserve car with id: " + id + " and location: " + location);
				if (!reserveCar(id, customerId, location))
				{
					Trace.warn("Failed.");
					return false;
				}
			}
			if (room)
			{
				Trace.warn("Trying to reserve room with id: " + id + " and location: " + location);
				if (!reserveRoom(id, customerId, location))
				{
					Trace.warn("Failed.");
					return false;
				}
			}
			Trace.warn("Itinerary reservation successful.");
		
			return true;
		}
    }

}
