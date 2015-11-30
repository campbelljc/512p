// -------------------------------
// Adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package master;

import java.util.*;
import java.util.Map.Entry;

import javax.jws.WebService;

import middle.MasterRecord;
import middle.Message;
import middle.MasterRecord.NamedMessage;
import middle.ServerName;
import middle.CrashPoint;

@WebService(endpointInterface = "master.ws.ResourceManager")
public class ResourceManagerImplMW implements master.ws.ResourceManager
{   
	public ResourceManagerImplMW()
	{
		System.out.println("Master starting.");
	}
		
    @Override
    public boolean addFlight(int id, int flightNumber, int numSeats, int flightPrice) { return false; }

    /**
     * Delete the entire flight.
     * This implies deletion of this flight and all its seats.  If there is a 
     * reservation on the flight, then the flight cannot be deleted.
     *
     * @return success.
     */   
    @Override
    public boolean deleteFlight(int id, int flightNumber) { return false; }

    /* Return the number of empty seats in this flight. */
    @Override
    public int queryFlight(int id, int flightNumber) { return -1; } 

    /* Return the price of a seat on this flight. */
    @Override
    public int queryFlightPrice(int id, int flightNumber) { return -1; }
    
    public boolean flightExists(int id, int flightNumber) { return false; }


    // Car operations //

    /* Add cars to a location.  
     * This should look a lot like addFlight, only keyed on a string location
     * instead of a flight number.
     */
    @Override
    public boolean addCars(int id, String location, int numCars, int carPrice) { return false; }
    
    /* Delete all cars from a location.
     * It should not succeed if there are reservations for this location.
     */		    
    @Override
    public boolean deleteCars(int id, String location) { return false; }

    /* Return the number of cars available at this location. */
    @Override
    public int queryCars(int id, String location) { return -1; }

    /* Return the price of a car at this location. */
    @Override
    public int queryCarsPrice(int id, String location) { return -1; }

    public boolean carExists(int id, String location) { return false; }


    // Room operations //
    
    /* Add rooms to a location.  
     * This should look a lot like addFlight, only keyed on a string location
     * instead of a flight number.
     */
    @Override
    public boolean addRooms(int id, String location, int numRooms, int roomPrice) { return false; }		    

    /* Delete all rooms from a location.
     * It should not succeed if there are reservations for this location.
     */
    @Override
    public boolean deleteRooms(int id, String location) { return false; }

    /* Return the number of rooms available at this location. */
    @Override
    public int queryRooms(int id, String location) { return -1; }

    /* Return the price of a room at this location. */
    @Override
    public int queryRoomsPrice(int id, String location) { return -1; }
    
    public boolean roomExists(int id, String location) { return false; }



    // Customer operations //
        
    /* Create a new customer and return their unique identifier. */
    @Override
    public int newCustomer(int id) { return -1; }
    
    /* Create a new customer with the provided identifier. */
    @Override
    public boolean newCustomerId(int id, int customerId) { return false; }

    /* Remove this customer and all their associated reservations. */
    @Override
    public boolean deleteCustomer(int id, int customerId) { return false; }

	@Override
	public void deleteReservationWithKey(int id, String key, int count) { }

    /* Return a bill. */
    @Override
    public String queryCustomerInfo(int id, int customerId) { return ""; }

    /* Reserve a seat on this flight. */
    @Override
    public boolean reserveFlight(int id, int customerId, int flightNumber) { return false; }

    /* Reserve a car at this location. */
    @Override
    public boolean reserveCar(int id, int customerId, String location) { return false; }

    /* Reserve a room at this location. */
    @Override
    public boolean reserveRoom(int id, int customerId, String location) { return false; }


    /* Reserve an itinerary. */
    @Override
    public boolean reserveItinerary(int id, int customerId, Vector flightNumbers, 
                                    String location, boolean car, boolean room) { return false; }
    
    @Override
    /**
     * Starts a transaction.
     * @return the transaction id.
     */
    public int start() { return -1; }
    
    @Override
    /**
     * Commits a transaction.
     * @param tid transaction id.
     */
    public boolean commit(int tid) { return false; }
    
    @Override
    /**
     * Aborts a transaction.
     * @param tid transaction id.
     */
    public boolean abort(int tid) { return false; }
    
    @Override
    /**
     * Soft system shutdown.
     */
    public boolean shutdown() { return false; }
    			
    
	@Override
	public boolean checkTransaction(int tid) { return false; }
	
	
	@Override
	/**
	 * 2PC - request a vote from this participant.
	 * @param tid
	 * @return true = YES, false = NO
	 */
	public boolean voteRequest(int tid) { return false; }
	
	@Override
	public void crashAtPoint(String which, middle.CrashPoint pt) { }
	
	@Override
	public void crash(String which) { }
	
	@Override
	public void selfDestruct(middle.CrashPoint pt) { }
	
	@Override
	public void checkForCrash(middle.CrashPoint pt) { }
	
	@Override
	public void setVoteReply2(boolean commit_) { }
	
	@Override
	public void setVoteReply(String which, boolean commit_) { }
	
	@Override
	public ServerName getName() { return ServerName.Null; }
	
	@Override
	public void setName(ServerName sName_) { }
	
	@Override
	public boolean getDecision(int tid) { return false; }
}
