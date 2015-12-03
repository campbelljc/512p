package middle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Collections;
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
import middle.CrashPoint;

/**
 * Transaction manager existing locally on the middleware.
 */
public class TransactionManager {
	
	private static final int TTL_CHECK_INTERVAL = 25; // seconds
	private static final int VOTE_REQUEST_TIMEOUT = 10; // seconds
	
	private LockManager lockMgr = new LockManager();
	private HashMap<Integer, Transaction> txnMap;
	private AtomicInteger nextID = new AtomicInteger(0);
	private WSClient[] resourceManagers; // [flight, car, hotel]
	private MasterRecord record;
	private ResourceManagerImplMW mw;

	private boolean isShutdown = false;

	public TransactionManager(WSClient[] resourceManagers, ResourceManagerImplMW mw){ 
		System.out.println("TM starting up");
		this.resourceManagers = resourceManagers;
		this.mw = mw;
		loadTransactions();
		this.record = MasterRecord.loadLog(ServerName.TM);
		if (!record.isEmpty()){
			recover();
		}
	}

	private void recover(){
		Set<Entry<Integer,ArrayList<NamedMessage>>> logEntries = record.getEntrySet();
		for(Entry<Integer,ArrayList<NamedMessage>> e : logEntries){
			Integer tid = e.getKey();
			ArrayList<NamedMessage> messages = e.getValue();
			NamedMessage lastMessage = messages.get(messages.size() - 1);
			switch(lastMessage.msg)
			{
				case TM_ABORTED:
				{
					System.out.println("TM- Txn " + tid.toString() + " aborted - no recovery.");
					break;
				}
				case TM_COMMITTED:
				{
					System.out.println("TM- Txn " + tid.toString() + " committed - no recovery.");
					break;
				}
				case TM_START_ABORT:
				{
					System.out.println("TM- Txn " + tid.toString() + " crashed after starting abort - aborting...");
					abort(tid);
					break;
				}
				case TM_START_COMMIT:
				{
					System.out.println("TM- Txn " + tid.toString() + " crashed after starting commit - committing...");
					commit(tid);
					break;
				}
				case TM_NO_VOTE:
				{
					System.out.println("TM- Txn " + tid.toString() + " crashed after a NO vote - aborting...");
					record.log(tid, Message.TM_PREPARED, null);
					abort(tid);
					break;
				}
				case TM_YES_VOTE:
				{
					Set<DType> dtypes = txnMap.get(tid).getDTypes();
					System.out.println("TM- Txn " + tid.toString() + " crashed after receiving some YES votes - continuing commit...");
					if (lastMessage.name == ServerName.RM_HOTEL) {
						record.log(tid, Message.TM_PREPARED, null);
						doCommit(tid);
					}
					else if(lastMessage.name == ServerName.RM_CAR) {
						// still need to ask hotel
						boolean decision = true;
						if(dtypes.contains(DType.ROOM)){
							decision = sendVoteRequest(tid, resourceManagers[2]);
						}
						record.log(tid, Message.TM_PREPARED, null);
						if(decision){
							doCommit(tid);
						}
						else{
							abort(tid);
						}
					}
					else {
						// still need to ask hotel and car
						boolean decision = true;
						if(dtypes.contains(DType.CAR)){
							decision = sendVoteRequest(tid, resourceManagers[1]);
						}
						if(decision && dtypes.contains(DType.ROOM)){
							decision = sendVoteRequest(tid, resourceManagers[2]);
						}
						record.log(tid, Message.TM_PREPARED, null);
						if(decision){
							doCommit(tid);
						}
						else{
							abort(tid);
						}
					}
				}
				case TM_PREPARED:
				{
					System.out.println("TM- Txn " + tid.toString() + " crashed after preparing - retrieving decision...");
					Message secondToLastMessage = messages.get(messages.size() - 2).msg;
					if(secondToLastMessage == Message.TM_NO_VOTE){
						abort(tid);
					}
					else{
						doCommit(tid);
					}
					break;
				}
				case TM_SENT_ABORT:
				{
					Set<DType> dtypes = txnMap.get(tid).getDTypes();
					System.out.println("TM- Txn " + tid.toString() + " crashed after sending some abort decisions - continuing abort...");
					if(lastMessage.name == ServerName.RM_HOTEL){
						if(dtypes.contains(DType.CUSTOMER)){
							sendMWAbort(tid);			
						}
						completeAbort(tid);
					}
					else if(lastMessage.name == ServerName.RM_CAR){
						// still need to send an abort to hotel
						if(dtypes.contains(DType.ROOM)){
							sendRMAbort(tid, resourceManagers[2]);
						}
						if(dtypes.contains(DType.CUSTOMER)){
							sendMWAbort(tid);			
						}
						completeAbort(tid);
					}
					else{
						// still need to send an abort to hotel and car
						if(dtypes.contains(DType.CAR)){
							sendRMAbort(tid, resourceManagers[1]);
						}
						if(dtypes.contains(DType.ROOM)){
							sendRMAbort(tid, resourceManagers[2]);
						}
						if(dtypes.contains(DType.CUSTOMER)){
							sendMWAbort(tid);			
						}
						completeAbort(tid);
					}
				}
				case TM_SENT_COMMIT:
				{
					Set<DType> dtypes = txnMap.get(tid).getDTypes();
					System.out.println("TM- Txn " + tid.toString() + " crashed after sending some commit decisions - continuing commit...");
					if(lastMessage.name == ServerName.RM_HOTEL){
						if(dtypes.contains(DType.CUSTOMER)){
							sendMWCommit(tid);			
						}
						completeCommit(tid);
					}
					else if(lastMessage.name == ServerName.RM_CAR){
						// still need to send an abort to hotel
						if(dtypes.contains(DType.ROOM)){
							sendRMCommit(tid, resourceManagers[2]);
						}
						if(dtypes.contains(DType.CUSTOMER)){
							sendMWCommit(tid);			
						}
						completeCommit(tid);
					}
					else{
						// still need to send an abort to hotel and car
						if(dtypes.contains(DType.CAR)){
							sendRMCommit(tid, resourceManagers[1]);
						}
						if(dtypes.contains(DType.ROOM)){
							sendRMCommit(tid, resourceManagers[2]);
						}
						if(dtypes.contains(DType.CUSTOMER)){
							sendMWCommit(tid);			
						}
						completeCommit(tid);
					}
	
				}			
				default:
				{
					System.out.println("TM- Txn " + tid.toString() + "ERROR: UNEXPECTED MESSAGE IN TXN RECOVERY LOG");
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
		System.out.println("TM- Txn " + Integer.toString(tid) + " starting");

		// Spawn TTL checker
		final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleWithFixedDelay(new Runnable(){
			@Override
			public void run(){
				if(txnMap.get(tid).ttlExpired() && !txnMap.get(tid).isClosed()){
					System.out.println("TM- Txn " + Integer.toString(tid) + " EXPIRED! Aborting...");
					abort(tid);
				}
			}
		}, 0, TTL_CHECK_INTERVAL, TimeUnit.SECONDS);
		saveTransactions();
		return tid;
	}
	
	/**
	 * Performs a commit operation.
	 * @param tid the transaction ID.
	 */
	public boolean commit(int tid){
		Transaction txn = txnMap.get(tid);
		if ((txn == null) || txn.isClosed()){
			System.out.println("TM- Txn " + Integer.toString(tid) + " invalid commit attempt.");
			return false;
		}
		System.out.println("TM- Txn " + Integer.toString(tid) + " committing...");
		txn.close();
		
		record.log(tid, Message.TM_START_COMMIT, null);
		
		boolean commitSuccess = false;
		if (prepare(tid)){
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					doCommit(tid);
				}
			});
			t.start();
			commitSuccess = true;
		}
		else{
			// at least one resource manager did not say YES to the vote request.
			abort(tid);
		}
		System.out.println("Returning now to client...");
		return commitSuccess;
	}
	
	/**
	 * Performs an abort operation.
	 * @param tid the transaction ID.
	 */
	public boolean abort(int tid) {
		Transaction txn = txnMap.get(tid);
		if (txn == null){
			// txn doesn't exist. Ignore this abort request.
			return false;
		}

		CrashPoint cp = mw.getCrashPoint();
		if(cp != null && cp == CrashPoint.RM_AFTER_SND_VOTE_REPLY){
			try {
				System.out.println("Delaying...");
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		System.out.println("TM- Txn " + Integer.toString(tid) + " aborting...");

		txn.close();
		
		record.log(tid, Message.TM_START_ABORT, null);
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
				System.out.println("Running undo and sending abort messages to RMs now.");
			//	txn.undo();
				
				Set<DType> dtypes = txnMap.get(tid).getDTypes();
				if(dtypes.contains(DType.FLIGHT)){
					sendRMAbort(tid, resourceManagers[0]);
				}
				if(dtypes.contains(DType.CAR)){
					sendRMAbort(tid, resourceManagers[1]);
				}
				if(dtypes.contains(DType.ROOM)){
					sendRMAbort(tid, resourceManagers[2]);
				}
				if(dtypes.contains(DType.CUSTOMER)){
					sendMWAbort(tid);
				}
				
				mw.checkForCrash(CrashPoint.MW_AFTER_SND_ALL_DECISION);
				completeAbort(tid);
			}
		});
		t.start();
		return true;
	}
	
	private void doCommit(int tid){		
		// send commit to RMs, delay if we are simulating an after vote reply crash
		System.out.println("In doCommit()");

		CrashPoint cp = mw.getCrashPoint();
		if(cp != null && cp == CrashPoint.RM_AFTER_SND_VOTE_REPLY){
			try {
				System.out.println("Delaying...");
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Set<DType> dtypes = txnMap.get(tid).getDTypes();
		if(dtypes.contains(DType.FLIGHT)){
			sendRMCommit(tid, resourceManagers[0]);
		}
		if(dtypes.contains(DType.CAR)){
			sendRMCommit(tid, resourceManagers[1]);
		}
		if(dtypes.contains(DType.ROOM)){
			sendRMCommit(tid, resourceManagers[2]);
		}
		if(dtypes.contains(DType.CUSTOMER)){
			sendMWCommit(tid);
		}
		

		mw.checkForCrash(CrashPoint.MW_AFTER_SND_ALL_DECISION);

		// remove and release locks
		completeCommit(tid);
	}

	private void sendRMCommit(int tid, WSClient rm){
		System.out.println("TM- Txn " + Integer.toString(tid) + " sending RM commit...");
		boolean received = false;
		while(!received){
			try{
				rm.proxy.commit2(tid);
				received = true;
			} catch(Exception e){
				System.out.println("TM- Txn " + Integer.toString(tid) + " cannot reach rm, trying again...");
			}
		}

		record.log(tid, Message.TM_SENT_COMMIT, rm.proxy.getName()); 
		mw.checkForCrash(CrashPoint.MW_AFTER_SND_SOME_DECISION);	
	}
	
	private void sendRMAbort(int tid, WSClient rm){
		System.out.println("TM- Txn " + Integer.toString(tid) + " sending RM abort...");
		boolean received = false;
		while(!received){
			try{
				rm.proxy.abort2(tid);
				received = true;
			} catch(Exception e){
				System.out.println("TM- Txn " + Integer.toString(tid) + " cannot reach rm, trying again...");
			}
		}
		record.log(tid, Message.TM_SENT_COMMIT, rm.proxy.getName()); 	
		mw.checkForCrash(CrashPoint.MW_AFTER_SND_SOME_DECISION);
	}

	private void sendMWCommit(int tid){
		mw.commit2(tid);
		record.log(tid, Message.TM_SENT_COMMIT, ServerName.MW);
	}

	private void sendMWAbort(int tid){
		mw.abort2(tid);
		record.log(tid, Message.TM_SENT_ABORT, ServerName.MW);
	}

	private void completeCommit(int tid){
		System.out.println("TM- Txn " + Integer.toString(tid) + " commit completed!");
		
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
		record.log(tid, Message.TM_COMMITTED, null);
		saveTransactions();
	}
	
	private void completeAbort(int tid){
		System.out.println("TM- Txn " + Integer.toString(tid) + " abort completed!");
		
		txnMap.remove(tid);
		lockMgr.UnlockAll(tid);
		record.log(tid, Message.TM_ABORTED, null);
		saveTransactions();
	}

	
	private boolean prepare(int tid){
		System.out.println("TM- Txn " + Integer.toString(tid) + " preparing to commit...");
		
		boolean decision = true;
		Set<DType> dtypes = txnMap.get(tid).getDTypes();
		if(dtypes.contains(DType.FLIGHT)){
			decision = sendVoteRequest(tid, resourceManagers[0]);
			mw.checkForCrash(CrashPoint.MW_AFTER_SND_SOME_VOTE_REQ);
		}
		if(decision && dtypes.contains(DType.CAR)){
			decision = sendVoteRequest(tid, resourceManagers[1]);
			mw.checkForCrash(CrashPoint.MW_AFTER_SND_SOME_VOTE_REQ);
		}
		if(decision && dtypes.contains(DType.ROOM)){
			decision = sendVoteRequest(tid, resourceManagers[2]);
			mw.checkForCrash(CrashPoint.MW_AFTER_SND_SOME_VOTE_REQ);
		}
		mw.checkForCrash(CrashPoint.MW_AFTER_RCV_ALL_VOTE_REPLY);
		record.log(tid, Message.TM_PREPARED, null);
		mw.checkForCrash(CrashPoint.MW_AFTER_VOTE_DECISION);
		return decision;
	}
	
	private boolean sendVoteRequest(int tid, WSClient rm){
		System.out.println("TM- Txn " + Integer.toString(tid) + " sending vote requests...");
		
		ServerName rmName = rm.proxy.getName();
		// Execute voteRequest with a timeout
		ExecutorService executor = Executors.newCachedThreadPool();
		Callable<Boolean> task = new Callable<Boolean>() {
			public Boolean call() {
				return rm.proxy.voteRequest(tid);
			}
		};
		Future<Boolean> future = executor.submit(task);
		boolean decision = true;
		try {
			decision = future.get(VOTE_REQUEST_TIMEOUT, TimeUnit.SECONDS); 
		} catch (Exception e) {
			System.out.println("Something bad happened to our connection.");
			decision = false;
		}
		if(decision){
			System.out.println("Decided yes.");
			record.log(tid, Message.TM_YES_VOTE, rmName);
		}
		else{
			System.out.println("Decided no.");
			record.log(tid, Message.TM_NO_VOTE, rmName);
		}
		return decision;
	}
	
	/**
	 * Sets up a transactional read.
	 * @param tid the transaction ID.
	 * @param type the data item to read.
	 */
	public boolean requestRead(int tid, DType type){
		Transaction txn = txnMap.get(tid);
		if ((txn == null) || txn.isClosed()){
			return false;
		}
		System.out.println("TM- Txn " + Integer.toString(tid) + " requesting read...");
		txn.resetTTL();
		try {
			lockMgr.Lock(tid, Integer.toString(type.ordinal()), LockManager.READ);
		} catch (DeadlockException e) {
			System.out.println("TM- Txn " + Integer.toString(tid) + " encountered DEADLOCK after read request! Aborting...");
			abort(tid);
			return false;
		}
		saveTransactions();
		return true;
	}
	
	/**
	 * Sets up a transactional read.
	 * @param tid the transaction ID.
	 * @param type the data item to read.
	 * @param undoFunction the inverse of the write operation.
	 */
	public boolean requestWrite(int tid, DType type, Runnable undoFunction){
		Transaction txn = txnMap.get(tid);
		if ((txn == null) || txn.isClosed()){
			return false;
		}
		System.out.println("TM- Txn " + Integer.toString(tid) + " requesting write...");
		txn.resetTTL();
		try {
			lockMgr.Lock(tid, Integer.toString(type.ordinal()), LockManager.WRITE);
		} catch (DeadlockException e) {
			System.out.println("Txn " + tid + " encountered DEADLOCK after write request! Aborting...");
			abort(tid);
			return false;
		}
		txn.addUndoOp(undoFunction);
		txn.addDType(type);
		saveTransactions();
		return true;
	}
	
	public void removeLastUndoOp(int tid){
		System.out.println("TM- Txn " + Integer.toString(tid) + " removing last undo op...");
		txnMap.get(tid).removeLastUndoOp();
	}
		
	/**
	 * Check if there are any active transactions.
	 * @return true if there are active transactions, false otherwise
	 */
	public synchronized void shutdown(){
		System.out.println("TM shutting down...");
		while(!txnMap.isEmpty()){}
		isShutdown = true;
	}
	
	public boolean checkTransaction(int tid)
	{
		Transaction txn = txnMap.get(tid);
		return !((txn == null) || txn.isClosed());
	}
	
	private void loadTransactions(){
		System.out.println("Loading transactions...");
		try {
			FileInputStream fis = new FileInputStream(new File("transactions.log"));

			// load master record into class var.
			ObjectInputStream ois = new ObjectInputStream(fis);
			try {
				txnMap = (HashMap<Integer, Transaction>) ois.readObject();
				if (txnMap.isEmpty()){
					nextID = new AtomicInteger(0);
				}
				else{
					nextID = new AtomicInteger(Collections.max(txnMap.keySet()));
				}
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			fis.close();			
		} catch (IOException e) { 
			// does not exist, so create.
			System.out.println("No master record found on disk - creating new file.");
			txnMap = new HashMap<Integer, Transaction>();
			saveTransactions();
		}
	}
	
	private void saveTransactions(){
		try {
			FileOutputStream fos = new FileOutputStream("transactions.log");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(txnMap);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
