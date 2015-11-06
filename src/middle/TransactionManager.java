package middle;

import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import server.Trace;
import LockManager.DeadlockException;
import LockManager.LockManager;

/**
 * Transaction manager existing locally on the middleware.
 */
public class TransactionManager {
	
	private static final int TTL_CHECK_INTERVAL = 10; // seconds
	
	private LockManager lockMgr = new LockManager();
	private HashMap<Integer, Transaction> txnMap = new HashMap<Integer, Transaction>();
	private AtomicInteger nextID = new AtomicInteger(0);
	
	/**
	 * Starts a new transaction. 
	 * @return the transaction ID.
	 */
	public int start(){
		int tid = nextID.incrementAndGet();
		txnMap.put(tid, new Transaction());

		// Spawn TTL checker
		final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleWithFixedDelay(new Runnable(){
			@Override
			public void run(){
				if(txnMap.get(tid).ttlExpired()){
					Trace.warn("Transaction expired! Aborting transaction with ID " + Integer.toString(tid));
					abort(tid);
				}
			}
		}, 0, TTL_CHECK_INTERVAL, TimeUnit.SECONDS);

		return tid;
	}
	
	/**
	 * Performs a commit operation.
	 * @param tid the transaction ID.
	 */
	public void commit(int tid){
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
	}
	
	/**
	 * Performs an abort operation.
	 * @param tid the transaction ID.
	 */
	public void abort(int tid){
		txnMap.get(tid).undo();
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
	}
	
	/**
	 * Sets up a transactional read.
	 * @param tid the transaction ID.
	 * @param strData the data item to read.
	 */
	public void requestRead(int tid, String strData){
		txnMap.get(tid).resetTTL();
		try {
			lockMgr.Lock(tid, strData, LockManager.READ);
		} catch (DeadlockException e) {
			Trace.warn("Deadlock detected! Aborting transaction with ID " + Integer.toString(tid));
			abort(tid);
		}
	}
	
	/**
	 * Sets up a transactional read.
	 * @param tid the transaction ID.
	 * @param strData the data item to read.
	 * @param undoFunction the inverse of the write operation.
	 */
	public void requestWrite(int tid, String strData, Runnable undoFunction){
		txnMap.get(tid).resetTTL();
		try {
			lockMgr.Lock(tid, strData, LockManager.WRITE);
		} catch (DeadlockException e) {
			Trace.warn("Deadlock detected! Aborting transaction with ID " + Integer.toString(tid));
			abort(tid);
		}
		txnMap.get(tid).addUndoOp(undoFunction);
	}
	
	// requestWrite(0, "a", () -> addFlight(x, y, z));
	
	/**
	 * Check if there are any active transactions.
	 * @return true if there are active transactions, false otherwise
	 */
	public boolean transactionsRunning(){
		return !txnMap.isEmpty();
	}
}
