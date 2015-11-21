package middle;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import server.Trace;
import LockManager.DeadlockException;
import LockManager.LockManager;
import middle.ResourceManagerImplMW.DType;

/**
 * Transaction manager existing locally on the middleware.
 */
public class TransactionManager {
	
	private static final int TTL_CHECK_INTERVAL = 25; // seconds
	private static final int VOTE_REQUEST_TIMEOUT = 10; // seconds
	
	private LockManager lockMgr = new LockManager();
	private HashMap<Integer, Transaction> txnMap = new HashMap<Integer, Transaction>();
	private AtomicInteger nextID = new AtomicInteger(0);
	private WSClient[] resourceManagers;
	
	private boolean isShutdown = false;
	
	public TransactionManager(WSClient[] resourceManagers){ 
		this.resourceManagers = resourceManagers;
	}
	
	/**
	 * Starts a new transaction. 
	 * @return the transaction ID.
	 */
	public synchronized int start(){
		if(isShutdown){
			Trace.error("System was shutdown, cannot start a new transaction");
			return -1;
		}
		int tid = nextID.incrementAndGet();
		txnMap.put(tid, new Transaction());
		System.out.println("Txn " + tid + " starting");

		// Spawn TTL checker
		final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleWithFixedDelay(new Runnable(){
			@Override
			public void run(){
				if(txnMap.get(tid).ttlExpired()){
					System.out.println("Txn " + tid + " EXPIRED! Aborting...");
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
	public boolean commit(int tid){
		if (txnMap.get(tid) == null)
		{
			// txn doesn't exist, or was already comitted/aborted. Ignore this commit request.
			MasterRecord.log(tid, "INVALID_TXN_COMMIT");
			return false;
		}
		
		MasterRecord.log(tid, "CLIENT_COMMIT");
		boolean commitSuccess = false;
		
		if (prepare(tid)){
			// all resource managers said YES to vote request.
			MasterRecord.log(tid, "DECISION_YES");
			for(WSClient rm : resourceManagers){
				MasterRecord.log(tid, "COMMIT_SENT"); // TODO: rm identifier
				rm.proxy.commit();
			}
			MasterRecord.log(tid, "ALL_COMMITS_SENT");
			txnMap.remove(tid);
			lockMgr.UnlockAll(tid);
			commitSuccess = true;
			MasterRecord.log(tid, "TXN_COMPLETE");
		}
		else{
			// at least one resource manager did not say YES to the vote request.
			MasterRecord.log(tid, "DECISION_NO");
			abort(tid);
		}
		return commitSuccess;
	}
	
	/**
	 * Performs an abort operation.
	 * @param tid the transaction ID.
	 */
	public boolean abort(int tid) {
		if (txnMap.get(tid) == null)
		{
			// txn doesn't exist, or was already comitted/aborted. Ignore this abort request.
			MasterRecord.log(tid, "INVALID_TXN_ABORT");
			return false;
		}
		MasterRecord.log(tid, "CLIENT_ABORT");
		
		for(WSClient rm : resourceManagers){
			MasterRecord.log(tid, "ABORT_SENT"); // TODO: rm identifier
			rm.proxy.abort();
		}
		
		MasterRecord.log(tid, "ALL_ABORTS_SENT");
		txnMap.get(tid).undo(); // TODO: do we still need to undo, and if so, where? should anything be logged?
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
		MasterRecord.log(tid, "TXN_COMPLETE");
		
		return true;
	}
	
	/**
	 * Sets up a transactional read.
	 * @param tid the transaction ID.
	 * @param type the data item to read.
	 */
	public boolean requestRead(int tid, DType type){
		if (txnMap.get(tid) == null)
		{
			return false;
		}
		System.out.println("Txn " + tid + " requesting read");
		txnMap.get(tid).resetTTL();
		try {
			lockMgr.Lock(tid, Integer.toString(type.ordinal()), LockManager.READ);
		} catch (DeadlockException e) {
			System.out.println("Txn " + tid + " encountered DEADLOCK after read request! Aborting...");
			abort(tid);
			return false;
		}
		return true;
	}
	
	/**
	 * Sets up a transactional read.
	 * @param tid the transaction ID.
	 * @param type the data item to read.
	 * @param undoFunction the inverse of the write operation.
	 */
	public boolean requestWrite(int tid, DType type, Runnable undoFunction){
		if (txnMap.get(tid) == null)
		{
			return false;
		}
		System.out.println("Txn " + tid + " requesting write");
		txnMap.get(tid).resetTTL();
		try {
			lockMgr.Lock(tid, Integer.toString(type.ordinal()), LockManager.WRITE);
		} catch (DeadlockException e) {
			System.out.println("Txn " + tid + " encountered DEADLOCK after write request! Aborting...");
			abort(tid);
			return false;
		}
		txnMap.get(tid).addUndoOp(undoFunction);
		return true;
	}
	
	public void removeLastUndoOp(int tid){
		System.out.println("Txn " + tid + " removing last undo op.");
		txnMap.get(tid).removeLastUndoOp();
	}
		
	/**
	 * Check if there are any active transactions.
	 * @return true if there are active transactions, false otherwise
	 */
	public synchronized void shutdown(){
		System.out.println("Txn mgr shutting down");
		while(!txnMap.isEmpty()){}
		isShutdown = true;
	}
	
	public boolean checkTransaction(int tid)
	{
		return !(txnMap.get(tid) == null);
	}
	
	private boolean prepare(int tid){
		MasterRecord.log(tid, "PREPARE");
		boolean decision = true;
		for(WSClient rm : resourceManagers){
			MasterRecord.log(tid, "REQUEST_SENT"); // TODO: rm identifier
			
			// Execute voteRequest with a timeout
			ExecutorService executor = Executors.newCachedThreadPool();
			Callable<Boolean> task = new Callable<Boolean>() {
			   public Boolean call() {
				   return rm.proxy.voteRequest();
			   }
			};
			Future<Boolean> future = executor.submit(task);
			try {
			   decision = future.get(VOTE_REQUEST_TIMEOUT, TimeUnit.SECONDS); 
			} catch (TimeoutException | InterruptedException | ExecutionException e) {
			   decision = false;
			}
			
			if(!decision){
				MasterRecord.log(tid, "REQUEST_RESPONSE_NO"); // TODO: rm identifier
				break;
			}
			else{
				MasterRecord.log(tid, "REQUEST_RESPONSE_YES"); // TODO: rm identifier
			}
		}
		return decision;
	}
}
