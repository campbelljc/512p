package LockManager;

import java.util.BitSet;
import java.util.Vector;

public class LockManager
{
    public static final int READ = 0;
    public static final int WRITE = 1;
    
    private static int TABLE_SIZE = 4096;
    private static int DEADLOCK_TIMEOUT = 30000;
    
    private static TPHashTable lockTable = new TPHashTable(LockManager.TABLE_SIZE);
    private static TPHashTable stampTable = new TPHashTable(LockManager.TABLE_SIZE);
    private static TPHashTable waitTable = new TPHashTable(LockManager.TABLE_SIZE);
    
    public LockManager() {
        super();
    }
    
    public boolean Lock(int xid, String strData, int lockType) throws DeadlockException {
    	String locks[] = { "read", "write" };
    	String vars[] = { "a", "b" };
    	System.out.println("Txn " + xid + " trying to get " + locks[lockType] + " lock on data item " + strData); // vars[Integer.parseInt(strData)]);
        // if any parameter is invalid, then return false
        if (xid < 0) { 
            return false;
        }
        
        if (strData == null) {
            return false;
        }
        
        if ((lockType != TrxnObj.READ) && (lockType != TrxnObj.WRITE)) { 
            return false;
        }
        
        // two objects in lock table for easy lookup.
        TrxnObj trxnObj = new TrxnObj(xid, strData, lockType);
        DataObj dataObj = new DataObj(xid, strData, lockType);
        
        // return true when there is no lock conflict or throw a deadlock exception.
        try {
            boolean bConflict = true;
            BitSet bConvert = new BitSet(1);
            while (bConflict) {
                synchronized (this.lockTable) {
                    // check if this lock request conflicts with existing locks
                    bConflict = LockConflict(dataObj, bConvert);
                    if (!bConflict) {
                        // no lock conflict
                        synchronized (this.stampTable) {
                            // remove the timestamp (if any) for this lock request
                            TimeObj timeObj = new TimeObj(xid);
                            this.stampTable.remove(timeObj);
                        }
                        synchronized (this.waitTable) {
                            // remove the entry for this transaction from waitTable (if it
                            // is there) as it has been granted its lock request
                            WaitObj waitObj = new WaitObj(xid, strData, lockType);
                            this.waitTable.remove(waitObj);
                        }
                         
                        if (bConvert.get(0) == true) {
							// 0 is set to true in LockConflict if we are getting a write lock and we already have a read lock.
							// We have to check if anyone else also has a read lock.
							// If so, then we must wait, otherwise we can continue and change the read lock to write.
							
					        Vector vect = this.lockTable.elements(dataObj);
							//System.out.println("Lock :: vect :: " + vect.toString());
							
							boolean otherSharedLock = false;
							for (int i = 0; i < vect.size(); i++)
							{
								DataObj d2 = (DataObj)vect.elementAt(i);
								if (d2.getXId() != dataObj.getXId())
								{ // the d2 transaction has a read or write lock on the data object.
									otherSharedLock = true;
								}
							}
							
							if (otherSharedLock)
							{ // wait until that lock is released
								System.out.println("Txn " + xid + " can't convert read lock to write lock (other read lock exists).");
								bConflict = true;
							}
							else
							{ // we are fine, no other txns have a lock on this data item
								// convert our read lock to a write lock
						//		System.out.println("Txn " + xid + " starting to convert read lock to write lock.");
					//			System.out.println(this.lockTable.toString());
								DataObj d = (DataObj)vect.elementAt(0);
								d.setLockType(TrxnObj.WRITE);
								Vector v2 = this.lockTable.elements(trxnObj);
				//				System.out.println("Lock :: v2 :: " + v2.toString());
								for (int i = 0; i < v2.size(); i ++)
								{
				//					System.out.println(((TrxnObj)v2.elementAt(i)).getDataName() + " and strdata: " + strData);
									if (((TrxnObj)v2.elementAt(i)).getDataName().equals(strData))
									{
				//						System.out.println("Found...setting.");
										((TrxnObj)v2.elementAt(i)).setLockType(TrxnObj.WRITE);
										break;
									}
								}
								System.out.println("Txn " + xid + " converting read lock to write lock.");
				//				System.out.println(this.lockTable.toString());
							}
                        }
						else {
                            // a lock request that is not lock conversion
                            this.lockTable.add(trxnObj);
                            this.lockTable.add(dataObj);
							System.out.println("Txn " + xid + " adding lock on data item " + trxnObj.getDataName());
                        }
                    }
                }
                if (bConflict) {
                    // lock conflict exists, wait
                    WaitLock(dataObj);
                }
            }
        } 
        catch (DeadlockException deadlock) {
            throw deadlock;
        }
        catch (RedundantLockRequestException redundantlockrequest) {
              // just ignore the redundant lock request
			  System.out.println("Txn " + xid + " requesting lock it already had, continuing");
            return true;
        } 

        System.out.println("Txn " + xid + " succeeded.");
        return true;
    }

    
    // remove all locks for this transaction in the lock table.
    public boolean  UnlockAll(int xid) {
    	System.out.println("Txn " + xid + " unlocking all locks.");
        // if any parameter is invalid, then return false
        if (xid < 0) {
            return false;
        }

        TrxnObj trxnQueryObj = new TrxnObj(xid, "", -1);  // Only used in elements() call below.
        synchronized (this.lockTable) {
            Vector vect = this.lockTable.elements(trxnQueryObj);
	//		System.out.println("UnlockAll :: vect :: " + vect.toString());

            TrxnObj trxnObj;
            Vector waitVector;
            WaitObj waitObj;
            int size = vect.size();
                                                
            for (int i = (size - 1); i >= 0; i--) {
                
//				System.out.println("UnlockAll :: lockTable :: " + lockTable.toString());
                trxnObj = (TrxnObj) vect.elementAt(i);
//				System.out.println("UnlockAll :: for :: trxnObj :: " + trxnObj.toString());
                if (!this.lockTable.remove(trxnObj))
					System.out.println("We couldn't remove the transaction object");

                DataObj dataObj = new DataObj(trxnObj.getXId(), trxnObj.getDataName(), trxnObj.getLockType());
	//			System.out.println("UnlockAll :: for :: dataObj :: " + dataObj.toString());
                if (!this.lockTable.remove(dataObj))
					System.out.println("We couldn't remove the data object");
				System.out.println("Txn " + xid + " removing lock on " + trxnObj.getDataName() + " and notifying waiting transactions.");
	//			System.out.println("UnlockAll :: lockTable2 :: " + lockTable.toString());
                                        
                // check if there are any waiting transactions. 
                synchronized (this.waitTable) {
                    // get all the transactions waiting on this dataObj
                    waitVector = this.waitTable.elements(dataObj);
                    int waitSize = waitVector.size();
                    for (int j = 0; j < waitSize; j++) {
                        waitObj = (WaitObj) waitVector.elementAt(j);
				//		System.out.println("UnlockAll :: for :: synchronized :: for :: waitObj :: " + waitObj.toString());
                        if (waitObj.getLockType() == LockManager.WRITE) {
                            if (j == 0) {
                                // get all other transactions which have locks on the
                                // data item just unlocked. 
                                Vector vect1 = this.lockTable.elements(dataObj);
					//			System.out.println("UnlockAll :: vect1 :: " + vect1.toString());
								
								DataObj d2 = (DataObj) vect1.elementAt(0);
                                
                                // remove interrupted thread from waitTable only if no
                                // other transaction has locked this data item
                                if (vect1.size() == 0 || (vect1.size() == 1 && d2.getXId() == waitObj.getXId() && d2.getDataName().equals(waitObj.getDataName()))) {
                                    this.waitTable.remove(waitObj);     
									if (d2 != null) this.lockTable.remove(d2);
									
						//			System.out.println("UnlockAll :: waitTable :: " + this.waitTable.toString());						
                                    
                                    try {
                                        synchronized (waitObj.getThread())    {
                                            waitObj.getThread().notify();
                                        }    
                                    }
                                    catch (Exception e)    {
                                        System.out.println("Exception on unlock\n" + e.getMessage());
                                    }        
                                }
                                else {
                                    // some other transaction still has a lock on
                                    // the data item just unlocked. So, WRITE lock
                                    // cannot be granted.
									System.out.println("UnlockAll :: cannot grant");
									
                                    break;
                                }
                            }

                            // stop granting READ locks as soon as you find a WRITE lock
                            // request in the queue of requests
                            break;
                        } else if (waitObj.getLockType() == LockManager.READ) {
                            // remove interrupted thread from waitTable.
                            this.waitTable.remove(waitObj);    
                            
                            try {
                                synchronized (waitObj.getThread()) {
                                    waitObj.getThread().notify();
                                }    
                            }
                            catch (Exception e) {
                                System.out.println("Exception e\n" + e.getMessage());
                            }
                        }
                    }
                } 
            }
        } 

        return true;
    }

    
    // returns true if the lock request on dataObj conflicts with already existing locks. If the lock request is a
    // redundant one (for eg: if a transaction holds a read lock on certain data item and again requests for a read
    // lock), then this is ignored. This is done by throwing RedundantLockRequestException which is handled 
    // appropriately by the caller. If the lock request is a conversion from READ lock to WRITE lock, then bitset 
    // is set. 
    
    private boolean LockConflict(DataObj dataObj, BitSet bitset) throws DeadlockException, RedundantLockRequestException {
        Vector vect = this.lockTable.elements(dataObj);
//		System.out.println("LockConflict :: vect :: " + vect.toString());
        DataObj dataObj2;
        int size = vect.size();
        
        // as soon as a lock that conflicts with the current lock request is found, return true
        for (int i = 0; i < size; i++) {
	//		if (vect.elementAt(i) instanceof TrxnObj)
	//			continue;
            dataObj2 = (DataObj) vect.elementAt(i);
			
            if (dataObj.getXId() == dataObj2.getXId()) {    
                // the transaction already has a lock on this data item which means that it is either
                // relocking it or is converting the lock
                if (dataObj.getLockType() == DataObj.READ) {    
                    // since transaction already has a lock (may be READ, may be WRITE. we don't
                    // care) on this data item and it is requesting a READ lock, this lock request
                    // is redundant.
                    throw new RedundantLockRequestException(dataObj.getXId(), "Redundant READ lock request");
                } else if (dataObj.getLockType() == DataObj.WRITE) {
                    // transaction already has a lock and is requesting a WRITE lock
                    // now there are two cases to analyze here
					if (dataObj2.getLockType() == DataObj.WRITE)
					{ // we already had a write lock.
						throw new RedundantLockRequestException(dataObj.getXId(), "Redundant WRITE lock request");
					}
					else if (dataObj2.getLockType() == DataObj.READ)
					{ // we had a read lock and we have to convert it now...
						bitset.set(0); //set 0 to true
						return false;
					}
                    // (1) transaction already had a READ lock
                    // (2) transaction already had a WRITE lock
                    // Seeing the comments at the top of this function might be helpful
                    // *** ADD CODE HERE *** to take care of both these cases
                }
            } 
            else {
                if (dataObj.getLockType() == DataObj.READ) {
                    if (dataObj2.getLockType() == DataObj.WRITE) {
                        // transaction is requesting a READ lock and some other transaction
                        // already has a WRITE lock on it ==> conflict
                        System.out.println("Want READ, someone (" + dataObj2.getXId() + ") has WRITE");
                        return true;
                    }
                    else {
                        // do nothing 
                    }
                } else if (dataObj.getLockType() == DataObj.WRITE) {
                    // transaction is requesting a WRITE lock and some other transaction has either
                    // a READ or a WRITE lock on it ==> conflict
                    System.out.println("Want WRITE, someone has READ or WRITE");
                    return true;
                }
            }
        }
        
        // no conflicting lock found, return false
        return false;

    }
    
    private void WaitLock(DataObj dataObj) throws DeadlockException {
        // Check timestamp or add a new one.
        // Will always add new timestamp for each new lock request since
        // the timeObj is deleted each time the transaction succeeds in
        // getting a lock (see Lock() )
        
        TimeObj timeObj = new TimeObj(dataObj.getXId());
        TimeObj timestamp = null;
        long timeBlocked = 0;
        Thread thisThread = Thread.currentThread();
        WaitObj waitObj = new WaitObj(dataObj.getXId(), dataObj.getDataName(), dataObj.getLockType(), thisThread);

        synchronized (this.stampTable) {
            Vector vect = this.stampTable.elements(timeObj);
            if (vect.size() == 0) {
                // add the time stamp for this lock request to stampTable
                this.stampTable.add(timeObj);
                timestamp = timeObj;
            } else if (vect.size() == 1) {
                // lock operation could have timed out; check for deadlock
                TimeObj prevStamp = (TimeObj)vect.firstElement();
                timestamp = prevStamp;
                timeBlocked = timeObj.getTime() - prevStamp.getTime();
                if (timeBlocked >= LockManager.DEADLOCK_TIMEOUT) {
                    // the transaction has been waiting for a period greater than the timeout period
                    cleanupDeadlock(prevStamp, waitObj);
                }
            } else {
                // should never get here. shouldn't be more than one time stamp per transaction
                // because a transaction at a given time the transaction can be blocked on just one lock
                // request. 
            }
        } 
        
        // suspend thread and wait until notified...

        synchronized (this.waitTable) {
            if (! this.waitTable.contains(waitObj)) {
                // register this transaction in the waitTable if it is not already there 
                this.waitTable.add(waitObj);
            }
            else {
                // else lock manager already knows the transaction is waiting.
            }
        }
        
        synchronized (thisThread) {
            try {
                thisThread.wait(LockManager.DEADLOCK_TIMEOUT - timeBlocked);
				System.out.println("Txn " + dataObj.getXId() + " waiting on data item " + dataObj.getDataName());
                TimeObj currTime = new TimeObj(dataObj.getXId());
                timeBlocked = currTime.getTime() - timestamp.getTime();
                if (timeBlocked >= LockManager.DEADLOCK_TIMEOUT) {
                    // the transaction has been waiting for a period greater than the timeout period
                    cleanupDeadlock(timestamp, waitObj);
                }
                else {
                    return;
                }
            }
            catch (InterruptedException e) {
                System.out.println("Thread interrupted?");
            }
        }
    }
    

    // cleanupDeadlock cleans up stampTable and waitTable, and throws DeadlockException
    private void cleanupDeadlock(TimeObj tmObj, WaitObj waitObj)
        throws DeadlockException
    {
        synchronized (this.stampTable) {
            synchronized (this.waitTable) {
                this.stampTable.remove(tmObj);
                this.waitTable.remove(waitObj);
            }
        }
        throw new DeadlockException(waitObj.getXId(), "Sleep timeout...deadlock.");
    }
}
