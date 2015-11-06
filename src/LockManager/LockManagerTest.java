package LockManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

class LockManagerTest {
    public static void main (String[] args)
    {
        TxnThread t1, t2;
    	LockManager lm = new LockManager ();
        TxnSimul sim = new TxnSimul(lm, 0, 0, 0, 4000, // txnid, datumid, locktype, amt_secs_to_sleep_after_command_issuing
        								0, 1, 1, 0,
        								1, 1, 1, 1000,
        								1, 0, 1, 0);
        t1 = new TxnThread(0, sim, lm);
        t2 = new TxnThread(1, sim, lm);
        t1.start();
        t2.start();
/*	t1 = new MyThread (lm, 1);
	t2 = new MyThread (lm, 2);
	t1.start ();
	t2.start ();
 */   }
   
}

class TxnSimul
{
	List<Runnable>[] txnCmds = new List[2];
	int[] curCmdIndices = new int[2];
	
	public TxnSimul(LockManager lm, int... args) // (txn, dataitem, locktype, sleepafteramt)
	{
		txnCmds[0] = new ArrayList();
		txnCmds[1] = new ArrayList();
		for (int i = 0; i < args.length; i ++)
		{
			final int j = i;
			Runnable r = new Runnable() {
				@Override
				public void run() {
					try {
						lm.Lock(args[j], Integer.toString(args[j+1]), args[j+2]);
						if (args[j+3] > 0)
							Thread.currentThread().sleep(args[j+3]);
					} catch (DeadlockException e) {
		//				e.printStackTrace();
						System.out.println("Txn " + args[j] + " is deadlocked!");
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};
			
			txnCmds[args[i]].add(r);
			
			i += 3;
		}
	/*	for (int i = 0; i < 2; i ++)
		{
			final int j = i;
			System.out.println("...");
			Runnable r2 = new Runnable() {
				@Override
				public void run() {
					System.out.println("Unlocking all...");
					lm.UnlockAll(j);
				}
			};
			txnCmds[i].add(r2);
		}*/
	}
	Runnable getNextCommand(int txn)
	{
		if (txnCmds[txn].size() < curCmdIndices[txn]+1)
			return null;
		Runnable nextCmd = txnCmds[txn].get(curCmdIndices[txn]);
		curCmdIndices[txn] ++;
		return nextCmd;
	}
}

/*class MyThread extends Thread {
    LockManager lm;
    int threadId;

    public MyThread (LockManager lm, int threadId) {
        this.lm = lm;
	this.threadId = threadId;
    }

    public void run () {
        if (threadId == 1) {
	    try {
		lm.Lock (1, "a", LockManager.READ);
	    }
	    catch (DeadlockException e) {
	        System.out.println ("Deadlock1a.... ");
	    }

	    try {
	        this.sleep (4000);
	    }
	    catch (InterruptedException e) { }

	    try {
		lm.Lock (1, "b", LockManager.WRITE);
	    }
	    catch (DeadlockException e) {
	        System.out.println ("Deadlock1b.... ");
	    }
	    
	    lm.UnlockAll (1);
	}
	else if (threadId == 2) {
	    try {
		lm.Lock (2, "b", LockManager.READ);
	    }
	    catch (DeadlockException e) { 
	        System.out.println ("Deadlock2b.... ");
	    }

	    try {
	        this.sleep (1000);
	    }
	    catch (InterruptedException e) { }

	    try {
		lm.Lock (2, "a", LockManager.WRITE);
	    }
	    catch (DeadlockException e) { 
	        System.out.println ("Deadlock2a.... ");
	    }
	    
	    lm.UnlockAll (2);
	}
    }
}*/

class TxnThread extends Thread {
	int threadID;
    TxnSimul sim;
    LockManager lm;
    
    public TxnThread(int tid, TxnSimul sim_, LockManager lm_)
    {
    	threadID = tid;
    	sim = sim_;
    	lm = lm_;
    }

    public void run()
    {
    	while(true) {
    		Runnable r = sim.getNextCommand(threadID);
    		if (r == null)
    		{
    			break;
    		}
    		r.run();
    	}
		System.out.println("Txn " + threadID + " completed, unlocking locks.");
    	lm.UnlockAll(threadID);
    }
}

