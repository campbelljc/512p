package middle;

import java.util.ArrayList;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class MasterRecord implements Serializable
{
	String identifier;
	ArrayList<Integer> txnIds = new ArrayList<Integer>();
	ArrayList<Message> messages = new ArrayList<Message>();
	
	public enum Message {
		RM_RCV_COMMIT_REQUEST,
		RM_RCV_ABORT_REQUEST,
		RM_COMMIT_SUCCESS,
		RM_COMMIT_ABORTED
	}
	
	public MasterRecord(String identifier)
	{
		this.identifier = identifier;
	}
	
	public void log(int tid, Message msg)
	{
		txnIds.add(tid);
		messages.add(msg);
		saveLog();
	}
	
	public void saveLog()
	{
		try {
			FileOutputStream fos = new FileOutputStream(identifier + "_record.log");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(this);
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isEmpty()
	{
		return messages.isEmpty();
	}

	public static MasterRecord loadLog(String rmName)
	{
		System.out.println("Loading master record.");
		MasterRecord record = new MasterRecord(rmName);
		try {			
			FileInputStream fis = new FileInputStream(new File(rmName+"_record.log"));

			// load master record into class var.
			ObjectInputStream ois = new ObjectInputStream(fis);
			try {
				record = (MasterRecord) ois.readObject();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
			fis.close();			
		} catch (IOException e) { 
			// does not exist, so create.
			System.out.println("No master record found on disk - creating new file.");
			record.saveLog();
		}
		return record;
	}
}
