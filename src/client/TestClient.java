package TestClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public class TestClient extends WSTestClient implements Runnable {
	
	private static final int SLEEP_INTERVAL_RANGE = 100;
	
	Random rand = new Random();
	int numTxn;
	long sleepTime;
	Transaction[] transactions;
	
	public TestClient(String serviceName, String serviceHost, int servicePort, int numTxn, Transaction[] transactions, long sleepTime) {
		super(serviceName, serviceHost, servicePort);
		this.numTxn = numTxn;
		this.sleepTime = sleepTime;
		this.transactions = transactions;
	}
	
	public void variedSleep(){
		try{
			Thread.sleep(sleepTime - (SLEEP_INTERVAL_RANGE/2) + rand.nextInt(SLEEP_INTERVAL_RANGE));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	

	@Override
	public void run() {
		for(int i=0; i<numTxn; i++){
			for(Transaction t : transactions){
				t.run();
				variedSleep();
			}
		}
	}
	
	public static void main(String[] args){
		// params
		String serviceName = "mw";
		String serviceHost = "localhost";
		int servicePort = 9082;
		int numTxn = 50;
		Transaction[] transactions;
		
		// 5a - single TestClient
		transactions = new Transaction[]{new MWTxn(proxy), new AllRMTxn(proxy)};
		TestClient TestClient = new TestClient(serviceName, serviceHost, servicePort, numTxn, transactions, 0L);
		TestClient.run();
		System.out.print("Middleware-only transaction average response time: ");
		System.out.println(transactions[0].getAvgResponseTime());
		System.out.print("All RM transaction average response time: ");
		System.out.println(transactions[1].getAvgResponseTime());
		
		// 5b - multi TestClient
//		int numTestClients = 10;
//		long sleepTime = 500*1000000L;
//		transactions = new Transaction[]{new MWTxn(), new AllRMTxn()};
//		TestClient[] TestClients = new TestClient[numTestClients];
//		for(int i=0; i<numTestClients; i++){
//			TestClients[i] = new TestClient(serviceName, serviceHost, servicePort, numTxn, 
//					Arrays.copyOf(transactions, transactions.length), sleepTime);
//		}
//		Thread[] TestClientThreads = new Thread[numTestClients];
//		for(int i=0; i<numTestClients; i++){
//			TestClientThreads[i] = new Thread(TestClients[i]);
//			TestClientThreads[i].run();
//		}
//		for(Thread t : TestClientThreads){
//			try {
//				t.join();
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
//		for(TestClient c : TestClients)
		
		
		
	}
}

abstract class Transaction{
	
	private static final long NS_PER_S = 1000000000L;
	private int numRuns = 0;
	private long avgResponseTime = 0;
	ResourceManager proxy;
	
	public Transaction(ResourceManager proxy_) { proxy = proxy_; }
	
	public void run(){
		Long txnStart = System.nanoTime();
		int tid = proxy.start();
		execute(tid);
		proxy.commit(tid);
		Long txnEnd = System.nanoTime();
		avgResponseTime = avgResponseTime * numRuns + (txnEnd - txnStart);
	}
	
	public long getAvgResponseTime(){
		return avgResponseTime/NS_PER_S;
	}
	
	
	abstract protected void execute(int id);
}

class MWTxn extends Transaction{
	public MWTxn(ResourceManager proxy_) { super(proxy_); }

	@Override
	protected void execute(int id) {
		proxy.newCustomer(id);
		proxy.newCustomer(id);
		proxy.newCustomer(id);
	}
}

class AllRMTxn extends Transaction{
	public AllRMTxn(ResourceManager proxy_) { super(proxy_); }

	@Override
	protected void execute(int id) {
		proxy.queryFlight(id, 0);
		proxy.queryCars(id, "");
		proxy.queryRooms(id, "");
	}
	
}
