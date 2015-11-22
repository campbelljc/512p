package middle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MasterRecord {
	
	public MasterRecord(String identifier)
	{
		
	}
	
	public void log(int tid, String message)
	{
		
		saveLog();
	}
	
	public void saveLog()
	{
		
	}
	
	public boolean isEmpty()
	{
		
	}
	
	public static MasterRecord loadLog(String rmName)
	{
		System.out.println("Loading master record.");
		MasterRecord record = new MasterRecord(rmName);
		try {			
			// ref : http://www.mkyong.com/java/how-to-read-file-in-java-fileinputstream/
			FileInputStream fis = new FileInputStream(new File(rmName+"_master.log"));
			
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
