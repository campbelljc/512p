package middle;

import java.util.ArrayList;

/**
 * Object representation of a transaction.
 */
public class Transaction {
	
	private static final long TTL_MAX = 100000000000L; // nanoseconds
	private ArrayList<Runnable> undoOps;
	private long ttl;
	
	public Transaction(){
		ttl = System.nanoTime();
		undoOps = new ArrayList<Runnable>();
	}
	
	public void resetTTL(){
		ttl = System.nanoTime();
	}
	
	public boolean ttlExpired(){
		return (System.nanoTime() - ttl) > TTL_MAX;
	}
	
	public void addUndoOp(Runnable r){
		undoOps.add(r);
	}
	
	public void undo(){
		for(Runnable r : undoOps){
			r.run();
		}
	}
}