package LockManager;

import java.util.ArrayList;
import java.util.List;

class LockManagerTest {
    public static void main (String[] args)
    {
    	LockManager lm = new LockManager ();
    	TxnSimul[] txns = { new TxnSimul(lm, 1, 0, 0, 4000,
									// txnid, datumid, locktype, amt_secs_to_sleep_after_command_issuing
        									1, 1, 1, 0,
        									2, 1, 1, 1000,
        									2, 0, 1, 0) /*, // T1 reads A, writes B. T2 reads B, writes A.
    						new TxnSimul(lm, 1, 0, 0, 1000,
    										1, 0, 1, 0),  // T1 reads A then writes A.
    						new TxnSimul(lm, 1, 0, 0, 1000, // t1 read a
    										2, 0, 0, 20000, // t2 read a and wait a long time
    										1, 0, 1, 0), // t1 write a
    						new TxnSimul(lm, 1, 0, 0, 2000, // t1 read a and wait long time
    										2, 0, 0, 0, // t2 read a and finish
    										1, 0, 1, 0) */ // t1 write a
    						};
    	for (TxnSimul sim : txns)
    	{
    		boolean secondThread = sim.getNumTxns(1) > 0;
    		TxnThread t1, t2;
            t1 = new TxnThread(0, sim, lm);
            t2 = new TxnThread(1, sim, lm);
            t1.start();
            if (secondThread) t2.start();
            try {
				t1.join();
				if (secondThread) t2.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
            System.out.println("-----------\n-----------\n-----------");
    	}
    }
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
			
			txnCmds[args[i]-1].add(r);
			
			i += 3;
		}
	}
	Runnable getNextCommand(int txn)
	{
		if (txnCmds[txn].size() < curCmdIndices[txn]+1)
			return null;
		Runnable nextCmd = txnCmds[txn].get(curCmdIndices[txn]);
		curCmdIndices[txn] ++;
		return nextCmd;
	}
	int getNumTxns(int txn)
	{
		return txnCmds[txn].size();
	}
}

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

