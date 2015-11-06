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
	
	public void addUndoOp(Runnable undoFunction){
		undoOps.add(undoFunction);
	}
	
	public void removeLastUndoOp(){
		undoOps.remove(undoOps.size()-1);
	}
	
	public void undo(){
		for(Runnable r : undoOps){
			r.run();
		}
	}
}