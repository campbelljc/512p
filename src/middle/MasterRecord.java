package middle;

import java.util.ArrayList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public enum Message {
	RM_RCV_COMMIT_REQUEST,
	RM_RCV_ABORT_REQUEST,
	RM_COMMIT_SUCCESS,
	RM_COMMIT_ABORTED
}

public class MasterRecord implements Serializable
{
	String identifier;
	List<Integer> tIDs = new ArrayList();
	List<Message> msgs = new ArrayList();
	
	public MasterRecord(String identifier_)
	{
		identifier = identifier_;
	}
	
	public void log(int tid, String message)
	{
		System.out.println("Do not use this method, it's not even finished yet and it will never be!!! It's just here to stop compiler errors.");
	}
	
	public void log(int tid, Message msg)
	{
		tIDs.add(tid);
		msgs.add(msg);
		saveLog();
	}
	
	public void saveLog()
	{
		FileOutputStream fos = new FileOutputStream(identifier + "_record.log");
		ObjectOutputStream oos = new ObjectOutputStream(fos);
		oos.writeObject(this);
		oos.close();
	}
	
	public boolean isEmpty()
	{
		return msgs.size() == 0;
	}
	
	public static MasterRecord loadLog(String rmName)
	{
		System.out.println("Loading master record.");
		MasterRecord record = new MasterRecord(rmName);
		try {			
			// ref : http://www.mkyong.com/java/how-to-read-file-in-java-fileinputstream/
			FileInputStream fis = new FileInputStream(new File(rmName+"_record.log"));
			
			// load master record into class var.
			// ref : http://www.tutorialspoint.com/java/io/objectinputstream_readobject.htm
            ObjectInputStream ois = new ObjectInputStream(fis);
			record = (MasterRecord) ois.readObject();
			fis.close();			
		} catch (IOException e)
		{ // does not exist, so create.
			System.out.println("No master record found on disk - creating new file.");
			record.saveLog();
		}
		return record;
	}
}
