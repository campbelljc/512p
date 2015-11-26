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
	private WSClient[] resourceManagers;
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
	
	private synchronized void recover()
	{
		Message lastMessage = record.getLastMessage();
		switch(lastMessage)
		{
			case TM_TXN_COMPLETED:
			{ // nothing to do
				break;
			}
			case TM_START_PREPARE:
			{ // crash before sending vote request.
				// then abort
				abort(record.getLastTID());
				break;
			}
			case TM_SENT_VOTE_REQUEST:
			{ // crash after sending vote request
				// TODO: we didn't receive the answer to this request, so must resend it, and send others after it.
			}
			case TM_RCV_VOTE_NO:
			{ // crash after receiving some replies but not all
				// TODO: we know here that the txn will abort, right?
				abort(record.getLastTID());
				break;
			}
			case TM_RCV_VOTE_YES:
			{ // crash after receiving some replies but not all
				// TODO: send out remaining vote requests
			}
			case TM_DECIDED_YES:
			{ // crash after deciding but before sending decision
				// TODO: send commit requests
			}
			case TM_DECIDED_NO:
			{ // crash after deciding but before sending decision
				// TODO: send abort requests
			}
			case TM_SENT_COMMIT_REQUEST:
			{ // sent some commit reqs but not all
				// TODO: send remaining commit reqs
			}
			case TM_SENT_ABORT_REQUEST:
			{ // sent some abort reqs but not all
				// TODO: send remaining abort reqs
			}
			case TM_SENT_ALL_COMMIT_REQUESTS:
			{ // crash after sent all decisions
				// TODO: perform final tidying up
			}
			case TM_SENT_ALL_ABORT_REQUESTS:
			{ // crash after sent all decisions
				// TODO: perform final tidying up
			}
			
			default:
			{
				System.out.println("Error - we did not expect this log entry: " + lastMessage.name());
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
		if (txnMap.get(tid) == null)
		{
			// txn doesn't exist, or was already comitted/aborted. Ignore this commit request.
	//		record.log(tid, Message.TM_INVALID_COMMIT);
	//Is this necessary?
			return false;
		}
		
		record.log(tid, Message.TM_START_COMMIT);
		boolean commitSuccess = false;
		
		if (prepare(tid)){
			// all resource managers said YES to vote request.
			record.log(tid, Message.TM_DECIDED_YES);
			for(WSClient rm : resourceManagers){
				record.log(tid, Message.TM_SENT_COMMIT_REQUEST, rm.proxy.getName()); 
				rm.proxy.commit(tid);
			}
			record.log(tid, Message.TM_SENT_COMMIT_REQUEST, ServerName.MW);
			mw.commit2(tid);
			
			record.log(tid, Message.TM_SENT_ALL_COMMIT_REQUESTS);
			txnMap.remove(tid);
			lockMgr.UnlockAll(tid);
			commitSuccess = true;
			record.log(tid, Message.TM_TXN_COMPLETED);
		}
		else{
			// at least one resource manager did not say YES to the vote request.
			record.log(tid, Message.TM_DECIDED_NO);
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
	//		record.log(tid, Message.TM_INVALID_ABORT);
			return false;
		}
		
		record.log(tid, Message.TM_START_ABORT);
		txnMap.get(tid).undo();
		
		for(WSClient rm : resourceManagers){
			record.log(tid, Message.TM_SENT_ABORT_REQUEST, rm.proxy.getName());
			rm.proxy.abort(tid);
		}
		mw.abort2(tid);
		
		record.log(tid, Message.TM_SENT_ALL_ABORT_REQUESTS);
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
		record.log(tid, Message.TM_TXN_COMPLETED);
		
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
		record.log(tid, Message.TM_START_PREPARE);
		boolean decision = true;
		for(WSClient rm : resourceManagers){
			record.log(tid, Message.TM_SENT_VOTE_REQUEST, rm.proxy.getName()); // TODO: rm identifier
			
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
			
			if(!decision){
				record.log(tid, Message.TM_RCV_VOTE_NO, rm.proxy.getName());
				break;
			}
			else{
				record.log(tid, Message.TM_RCV_VOTE_YES, rm.proxy.getName());
			}
		}
		return decision;
	}
}
