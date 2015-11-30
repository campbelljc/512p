package middle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


public class MasterRecord implements Serializable
{
	private static final long serialVersionUID = 6527548573305769186L;

	public class NamedMessage implements Serializable{
		private static final long serialVersionUID = 2696865163488157540L;
		public Message msg;
		public ServerName name;
		public NamedMessage(Message msg, ServerName name) {
			this.msg = msg;
			this.name = name;
		}
	}
	
	ServerName identifier;
	HashMap<Integer, ArrayList<NamedMessage>> messageLog;
	
	private MasterRecord(ServerName identifier)
	{
		this.identifier = identifier;
		messageLog = new HashMap<Integer, ArrayList<NamedMessage>>();
	}
	
	public void log(int tid, Message msg, ServerName sName)
	{
		ArrayList<NamedMessage> messages = messageLog.get(tid);
		if(messages == null){
			messages = new ArrayList<NamedMessage>();
			messages.add(new NamedMessage(msg, null));
			messageLog.put(tid, messages);
		}
		else{
			messages.add(new NamedMessage(msg, null));
		}
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
	
	public Set<Entry<Integer, ArrayList<NamedMessage>>> getEntrySet(){
		return messageLog.entrySet();
	}
	
	public ArrayList<NamedMessage> getEntriesForTxn(int tid)
	{
		return messageLog.get(tid);
	}
	
	public boolean isEmpty()
	{
		return messageLog.isEmpty();
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
