package middle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
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
import middle.MasterRecord.NamedMessage;
import middle.Message;
import middle.ServerName;
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
	private WSClient[] resourceManagers; // [flight, car, room]
	private MasterRecord record;
	private ResourceManagerImplMW mw;

	private boolean isShutdown = false;

	public TransactionManager(WSClient[] resourceManagers, ResourceManagerImplMW mw){ 
		this.resourceManagers = resourceManagers;
		this.mw = mw;

		this.record = MasterRecord.loadLog(ServerName.TM);
		if (!record.isEmpty())
			recover();
	}

	private void recover(){
		Set<Entry<Integer,ArrayList<NamedMessage>>> logEntries = record.getEntrySet();
		for(Entry<Integer,ArrayList<NamedMessage>> e : logEntries){
			Integer tid = e.getKey();
			ArrayList<NamedMessage> messages = e.getValue();
			Message lastMessage = messages.get(messages.size() - 1).msg;
			switch(lastMessage)
				{
				case TM_ABORTED:
				{
					break;
				}
				case TM_COMMITTED:
				{
					break;
				}
				case TM_START_ABORT:
				{
					abort(tid);
					break;
				}
				case TM_START_COMMIT:
				{
					commit(tid);
					break;
				}
				case TM_NO_VOTE:
				{
					break;
				}
				case TM_YES_VOTE:
				{
					break;
				}
				case TM_PREPARED:
				{
					Message secondToLastMessage = messages.get(messages.size() - 2).msg;
					if(secondToLastMessage == Message.TM_NO_VOTE){
						abort(tid);
					}
					else{
						// send commit to RMs
						sendRMCommit(tid, resourceManagers);
	
						// send commit to MW
						sendMWCommit(tid);
	
						// remove and release locks
						completeCommit(tid);
					}
					break;
				}
				case TM_SENT_ABORT:
				{
	
				}
				case TM_SENT_COMMIT:
				{
	
				}			
				default:
				{
					System.out.println("UNEXPECTED MESSAGE IN LOG");
					break;
				}
			}
		}
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
		if (txnMap.get(tid) == null){
			// txn doesn't exist. Ignore this commit request.
			return false;
		}
		
		record.log(tid, Message.TM_START_COMMIT, null);
		
		boolean commitSuccess = false;
		if (prepare(tid)){			
			// send commit to RMs
			sendRMCommit(tid, resourceManagers);
			
			// send commit to MW
			sendMWCommit(tid);
			
			// remove and release locks
			completeCommit(tid);
			
			commitSuccess = true;
		}
		else{
			// at least one resource manager did not say YES to the vote request.
			abort(tid);
		}
		return commitSuccess;
	}
	
	/**
	 * Performs an abort operation.
	 * @param tid the transaction ID.
	 */
	public boolean abort(int tid) {
		if (txnMap.get(tid) == null){
			// txn doesn't exist. Ignore this abort request.
			return false;
		}
		
		record.log(tid, Message.TM_START_ABORT, null);
		txnMap.get(tid).undo();
		
		// send abort to RMs
		sendRMAbort(tid, resourceManagers);
		
		// send abort to MW
		sendMWAbort(tid);
		
		completeAbort(tid);
		
		return true;
	}
	
	private void sendRMCommit(int tid, WSClient[] rms){
		for(WSClient rm : rms){
			rm.proxy.commit(tid); // TODO: do we need a timeout?
			record.log(tid, Message.TM_SENT_COMMIT, rm.proxy.getName()); 	
		}
	}
	
	private void sendRMAbort(int tid, WSClient[] rms){
		for(WSClient rm : rms){
			rm.proxy.abort(tid); // TODO: do we need a timeout?
			record.log(tid, Message.TM_SENT_COMMIT, rm.proxy.getName()); 	
		}
	}
	
	private void sendMWCommit(int tid){
		mw.commit2(tid); // TODO: do we need a timeout?
		record.log(tid, Message.TM_SENT_COMMIT, ServerName.MW);
	}
	
	private void sendMWAbort(int tid){
		mw.abort2(tid);
		record.log(tid, Message.TM_SENT_ABORT, ServerName.MW);
	}
	
	private void completeCommit(int tid){
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
		record.log(tid, Message.TM_COMMITTED, null);
	}
	
	private void completeAbort(int tid){
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
		record.log(tid, Message.TM_ABORTED, null);
	}

	
	private boolean prepare(int tid){
		boolean decision = true;
		for(WSClient rm : resourceManagers){			
			// Execute voteRequest with a timeout
			ExecutorService executor = Executors.newCachedThreadPool();
			Callable<Boolean> task = new Callable<Boolean>() {
			   public Boolean call() {
				   return rm.proxy.voteRequest(tid);
			   }
			};
			Future<Boolean> future = executor.submit(task);
			try {
			   decision = future.get(VOTE_REQUEST_TIMEOUT, TimeUnit.SECONDS); 
			} catch (TimeoutException | InterruptedException | ExecutionException e) {
			   decision = false;
			}
			
			if(decision){
				record.log(tid, Message.TM_YES_VOTE, rm.proxy.getName());
			}
			else{
				record.log(tid, Message.TM_NO_VOTE, rm.proxy.getName());
				break;
			}
		}
		record.log(tid, Message.TM_PREPARED, null);
		return decision;
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
}
