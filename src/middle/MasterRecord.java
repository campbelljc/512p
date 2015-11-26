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
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	ServerName identifier;
	ArrayList<Integer> tIDs = new ArrayList<Integer>();
	ArrayList<Message> messages = new ArrayList<Message>();
	ArrayList<ServerName> serverNames = new ArrayList<ServerName>();
	
	private MasterRecord(ServerName identifier)
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
	
	public Message getLastMessage()
	{
		return messages.get(messages.size() - 1);
	}
	
	public int getLastTID() {
		return tIDs.get(tIDs.size() - 1);
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
