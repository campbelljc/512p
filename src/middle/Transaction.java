package middle;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import middle.ResourceManagerImplMW.DType;

/**
 * Object representation of a transaction.
 */
public class Transaction implements Serializable{
	
	private static final long serialVersionUID = 4104749287936713660L;
	private static final long TTL_MAX = 60000000000L; // nanoseconds
//	private ArrayList<Runnable> undoOps;
	private HashSet<DType> usedDTypes;
	private long ttl;
	private boolean closed;
	
	public Transaction(){
		ttl = System.nanoTime();
//		undoOps = new ArrayList<Runnable>();
		usedDTypes = new HashSet<ResourceManagerImplMW.DType>();
		closed = false;
	}
	
	public void resetTTL(){
		ttl = System.nanoTime();
	}
	
	public boolean ttlExpired(){
		return (System.nanoTime() - ttl) > TTL_MAX;
	}
	
	public void addUndoOp(Runnable undoFunction){
		//undoOps.add(undoFunction);
	}
	
	public void removeLastUndoOp(){
		//undoOps.remove(undoOps.size()-1);
	}
	
	public void undo() {
	/*	ArrayList<Runnable> curUndoOps = new ArrayList<Runnable>(undoOps);
		Collections.reverse(curUndoOps);
		for(Runnable r : curUndoOps) {
			r.run();
		}*/
	}
	
	public void addDType(DType dtype){
		usedDTypes.add(dtype);
	}
	
	public Set<DType> getDTypes(){
		return usedDTypes;
	}
	
	public void close() { 
		closed = true;
	}
	
	public boolean isClosed(){
		return closed;
	}
}