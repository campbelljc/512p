package middle;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Object representation of a transaction.
 */
public class Transaction {
	
	private static final long TTL_MAX = 60000000000L; // nanoseconds
	private ArrayList<Runnable> undoOps;
	private long ttl;
	private boolean closed;
	
	public Transaction(){
		ttl = System.nanoTime();
		undoOps = new ArrayList<Runnable>();
		closed = false;
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
	
	public void undo() {
		ArrayList<Runnable> curUndoOps = new ArrayList<Runnable>(undoOps);
		Collections.reverse(curUndoOps);
		for(Runnable r : curUndoOps) {
			r.run();
		}
	}
	
	public void close() { 
		closed = true;
	}
	
	public boolean isClosed(){
		return closed;
	}
}