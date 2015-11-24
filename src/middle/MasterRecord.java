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
	ServerName identifier;
	ArrayList<Integer> tIDs = new ArrayList<Integer>();
	ArrayList<Message> messages = new ArrayList<Message>();
	ArrayList<ServerName> serverNames = new ArrayList<ServerName>();
	
	public enum Message {
		RM_RCV_COMMIT_REQUEST,
		RM_RCV_ABORT_REQUEST,
		RM_COMMIT_SUCCESS,
		RM_COMMIT_ABORTED, 
		TM_TXN_COMPLETE, 
		TM_COMMITS_SENT, 
		TM_DECISION_YES, 
		TM_COMMIT_SENT_RM, 
		TM_DECISION_NO, 
		TM_START_COMMIT, 
		TM_INVALID_COMMIT, 
		TM_INVALID_ABORT, 
		TM_START_ABORT, 
		TM_ABORT_SENT_RM, 
		TM_ABORTS_SENT, 
		TM_PREPARE, 
		TM_SENT_REQUEST_RM, 
		TM_REQUEST_RESPONSE_NO_RM, 
		TM_REQUEST_RESPONSE_YES_RM
	}
	
	public enum ServerName {
		MW,
		RM_FLIGHT,
		RM_HOTEL,
		RM_CAR,
		TM
	}
	
	public MasterRecord(ServerName identifier)
	{
		this.identifier = identifier;
	}
	
	public void log(int tID, Message msg)
	{
		tIDs.add(tID);
		messages.add(msg);
		serverNames.add(null);
		saveLog();
	}
	
	public void log(int tID, Message msg, ServerName sName)
	{
		tIDs.add(tID);
		messages.add(msg);
		serverNames.add(sName);
		saveLog();
	}
	
	public void saveLog()
	{
		try {
			FileOutputStream fos = new FileOutputStream(identifier.name() + "_record.log");
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

	public static MasterRecord loadLog(ServerName rmName)
	{
		System.out.println("Loading master record.");
		MasterRecord record = new MasterRecord(rmName);
		try {
			FileInputStream fis = new FileInputStream(new File(rmName.name()+"_record.log"));

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
