// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package server.tcp;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import server.Car;
import server.Customer;
import server.Flight;
import server.RMHashtable;
import server.RMItem;
import server.ReservableItem;
import server.ReservedItem;
import server.Room;
import server.Trace;

/**
 * Modified implementation of the web service version to allow for Reflection.
 */
public class ResourceManager {
    
    protected RMHashtable m_itemHT = new RMHashtable();
    
    
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
    
    /**
     * Invoke a method with a string and a variable list of arguments using Reflection.
     * @param methodName Name of a public ResourceManager method.
     * @param args Variable list of args to call the method with.
     * @return the result of the method invocation, or null upon failure.
     */
    public Object invokeMethodByName(String methodName, Object... args){
    	Class<? extends ResourceManager> thisClass = this.getClass();
    	Method[] methods = thisClass.getDeclaredMethods();
    	for(Method m : methods){
    		if(m.getName().equals(methodName)){
    			try {
    				// TODO: bug is here -- doesn't like object[] for args
					return (Object) m.invoke(this, args);
				} catch (IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					Trace.error(e.getMessage());
				}
    		}
    	}
    	return null;
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
        Trace.info("RM::queryPrice(" + id + ", " + key + ") called.");
        ReservableItem curObj = (ReservableItem) readData(id, key);
        int value = 0; 
        if (curObj != null) {
            value = curObj.getPrice();
        }
        Trace.info("RM::queryPrice(" + id + ", " + key + ") OK: $" + value);
        return value;
    }
    
    // Flight operations //
    
    // Create a new flight, or add seats to existing flight.
    // Note: if flightPrice <= 0 and the flight already exists, it maintains 
    // its current price.
    
    public boolean addFlight(int id, int flightNumber, 
                             int numSeats, int flightPrice) {
		 synchronized(m_itemHT)
		 {
	         Trace.info("RM::addFlight(" + id + ", " + flightNumber 
	                 + ", $" + flightPrice + ", " + numSeats + ") called.");
	         Flight curObj = (Flight) readData(id, Flight.getKey(flightNumber));
	         if (curObj == null) {
	             // Doesn't exist; add it.
	             Flight newObj = new Flight(flightNumber, numSeats, flightPrice);
	             writeData(id, newObj.getKey(), newObj);
	             Trace.info("RM::addFlight(" + id + ", " + flightNumber 
	                     + ", $" + flightPrice + ", " + numSeats + ") OK.");
	         } else {
	             // Add seats to existing flight and update the price.
	             curObj.setCount(curObj.getCount() + numSeats);
	             if (flightPrice > 0) {
	                 curObj.setPrice(flightPrice);
	             }
	             writeData(id, curObj.getKey(), curObj);
	             Trace.info("RM::addFlight(" + id + ", " + flightNumber 
	                     + ", $" + flightPrice + ", " + numSeats + ") OK: "
	                     + "seats = " + curObj.getCount() + ", price = $" + flightPrice);
	         }
	         return(true);	 	
		 }
    }

    
    public boolean deleteFlight(int id, int flightNumber) {
		 synchronized(m_itemHT)
		 {
        	 return deleteItem(id, Flight.getKey(flightNumber));
		 }
    }

    // Returns the number of empty seats on this flight.
    
    public int queryFlight(int id, int flightNumber) {
        return queryNum(id, Flight.getKey(flightNumber));
    }

    // Returns price of this flight.
    public int queryFlightPrice(int id, int flightNumber) {
        return queryPrice(id, Flight.getKey(flightNumber));
    }
    // Car operations //

    // Create a new car location or add cars to an existing location.
    // Note: if price <= 0 and the car location already exists, it maintains 
    // its current price.
    
    public boolean addCars(int id, String location, int numCars, int carPrice) {
		 synchronized(m_itemHT)
		 {	
	        Trace.info("RM::addCars(" + id + ", " + location + ", " 
	                + numCars + ", $" + carPrice + ") called.");
	        Car curObj = (Car) readData(id, Car.getKey(location));
	        if (curObj == null) {
	            // Doesn't exist; add it.
	            Car newObj = new Car(location, numCars, carPrice);
	            writeData(id, newObj.getKey(), newObj);
	            Trace.info("RM::addCars(" + id + ", " + location + ", " 
	                    + numCars + ", $" + carPrice + ") OK.");
	        } else {
	            // Add count to existing object and update price.
	            curObj.setCount(curObj.getCount() + numCars);
	            if (carPrice > 0) {
	                curObj.setPrice(carPrice);
	            }
	            writeData(id, curObj.getKey(), curObj);
	            Trace.info("RM::addCars(" + id + ", " + location + ", " 
	                    + numCars + ", $" + carPrice + ") OK: " 
	                    + "cars = " + curObj.getCount() + ", price = $" + carPrice);
	        }
	        return(true);
		}
    }

    // Delete cars from a location.
    
    public boolean deleteCars(int id, String location) {
		 synchronized(m_itemHT)
		 {
        	 return deleteItem(id, Car.getKey(location));
		 }
    }

    // Returns the number of cars available at a location.
    
    public int queryCars(int id, String location) {
        return queryNum(id, Car.getKey(location));
    }

    // Returns price of cars at this location.
    
    public int queryCarsPrice(int id, String location) {
        return queryPrice(id, Car.getKey(location));
    }
    

    // Room operations //

    // Create a new room location or add rooms to an existing location.
    // Note: if price <= 0 and the room location already exists, it maintains 
    // its current price.
    
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) {
		 synchronized(m_itemHT)
		 {
	        Trace.info("RM::addRooms(" + id + ", " + location + ", " 
	                + numRooms + ", $" + roomPrice + ") called.");
	        Room curObj = (Room) readData(id, Room.getKey(location));
	        if (curObj == null) {
	            // Doesn't exist; add it.
	            Room newObj = new Room(location, numRooms, roomPrice);
	            writeData(id, newObj.getKey(), newObj);
	            Trace.info("RM::addRooms(" + id + ", " + location + ", " 
	                    + numRooms + ", $" + roomPrice + ") OK.");
	        } else {
	            // Add count to existing object and update price.
	            curObj.setCount(curObj.getCount() + numRooms);
	            if (roomPrice > 0) {
	                curObj.setPrice(roomPrice);
	            }
	            writeData(id, curObj.getKey(), curObj);
	            Trace.info("RM::addRooms(" + id + ", " + location + ", " 
	                    + numRooms + ", $" + roomPrice + ") OK: " 
	                    + "rooms = " + curObj.getCount() + ", price = $" + roomPrice);
	        }
	        return(true);
		}
    }

    // Delete rooms from a location.
    
    public boolean deleteRooms(int id, String location) {
		 synchronized(m_itemHT)
		 {
			 return deleteItem(id, Room.getKey(location));
		 }
    }

    // Returns the number of rooms available at a location.
    
    public int queryRooms(int id, String location) {
        return queryNum(id, Room.getKey(location));
    }
    
    // Returns room price at this location.
    
    public int queryRoomsPrice(int id, String location) {
        return queryPrice(id, Room.getKey(location));
    }


    // Customer operations //

    
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

	public void deleteReservationWithKey(int id, String key, int count) {
		synchronized(m_itemHT)
	 	{
	        ReservableItem item = (ReservableItem) readData(id, key);
	        item.setReserved(item.getReserved() - count);
	        item.setCount(item.getCount() + count);
	        Trace.info("RM::deleteCustomer(" + id + "): reserved/available = " 
	                + item.getReserved() + "/" + item.getCount());
		}
	}


	public void deleteCarReservationWithKey(int id, String key, int count) {
		deleteReservationWithKey(id, key, count);
	}
	
	public void deleteRoomReservationWithKey(int id, String key, int count) {
		deleteReservationWithKey(id, key, count);
	}
	
	public void deleteFlightReservationWithKey(int id, String key, int count) {
		deleteReservationWithKey(id, key, count);
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
  //      return reserveItem(id, customerId, 
    //            Flight.getKey(flightNumber), String.valueOf(flightNumber));
		synchronized(m_itemHT)
		{
			String key = Flight.getKey(flightNumber);
			String location = String.valueOf(flightNumber);
		
		    ReservableItem item = (ReservableItem) readData(id, key);
		    if (item == null) {
		        Trace.warn("RM::reserveFlight(" + id + ", " + customerId + ", " 
		                + key + ", " + location + ") failed: item doesn't exist.");
		        return false;
		    } else if (item.getCount() == 0) {
		        Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
		                + key + ", " + location + ") failed: no more items.");
		        return false;
		    } else {            
		        // Decrease the number of available items in the storage.
		        item.setCount(item.getCount() - 1);
		        item.setReserved(item.getReserved() + 1);
        
		        Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
		                + key + ", " + location + ") OK.");
		        return true;
			}
		}
    }

    // Add car reservation to this customer. 
    
    public boolean reserveCar(int id, int customerId, String location) {
		synchronized(m_itemHT)
	 	{
	     //   return reserveItem(id, customerId, Car.getKey(location), location);
			String key = Car.getKey(location);
	        ReservableItem item = (ReservableItem) readData(id, key);
	        if (item == null) {
	            Trace.warn("RM::reserveCar(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed: item doesn't exist.");
	            return false;
	        } else if (item.getCount() == 0) {
	            Trace.warn("RM::reserveCar(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") failed: no more items.");
	            return false;
	        } else {            
	            // Decrease the number of available items in the storage.
	            item.setCount(item.getCount() - 1);
	            item.setReserved(item.getReserved() + 1);
            
	            Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
	                    + key + ", " + location + ") OK.");
	            return true;
			}
		}
    }

    // Add room reservation to this customer. 
    
    public boolean reserveRoom(int id, int customerId, String location) {
		synchronized(m_itemHT)
	 	{
	    //    return reserveItem(id, customerId, Room.getKey(location), location);
			String key = Room.getKey(location);
		    ReservableItem item = (ReservableItem) readData(id, key);
		    if (item == null) {
		        Trace.warn("RM::reserveRoom(" + id + ", " + customerId + ", " 
		                + key + ", " + location + ") failed: item doesn't exist.");
		        return false;
		    } else if (item.getCount() == 0) {
		        Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
		                + key + ", " + location + ") failed: no more items.");
		        return false;
		    } else {            
		        // Decrease the number of available items in the storage.
		        item.setCount(item.getCount() - 1);
		        item.setReserved(item.getReserved() + 1);
        
		        Trace.warn("RM::reserveItem(" + id + ", " + customerId + ", " 
		                + key + ", " + location + ") OK.");
		        return true;
			}
		}
	}
    

    // Reserve an itinerary.
  
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers,
                                    String location, boolean car, boolean room) {
        return false;
    }

}
