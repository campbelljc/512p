package client;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

public class TestClient extends WSClient implements Runnable {
	
	private static final int MS_PER_S = 1000;
		
	Random rand = new Random();
	int numTxn;
	int sleepTime;
	int sleepVariance;
	Transaction[] transactions;
	
	public TestClient(String serviceName, String serviceHost, int servicePort, int numTxn, 
			Transaction[] transactions, int sleepTime, int sleepVariance) {
		super(serviceName, serviceHost, servicePort);
		this.numTxn = numTxn;
		this.sleepTime = sleepTime;
		this.sleepVariance = sleepVariance;
		this.transactions = transactions;
		for(Transaction t : transactions){
			t.setProxy(proxy);
		}
	}
	
	
	public double[] getAverageResponseTimes(){
		double[] times = new double[transactions.length];
		for(int i=0; i < transactions.length; i++){
			times[i] = transactions[i].getAvgResponseTime();
		}
		return times;
	}
	
	public void variedSleep(){
		if(sleepTime > 0){
			try{
				Thread.sleep((sleepTime - sleepVariance) + rand.nextInt(sleepVariance));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
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
	
	
	
	public static void runExperiment(String serviceName, String serviceHost, int servicePort, int numClients, int sleepTime, int sleepVariance){
		int numTxn = 5;
		
		TestClient[] clients = new TestClient[numClients];
		Thread[] threads = new Thread[numClients];
		
		for(int i=0; i < numClients; i++){
			clients[i] = new TestClient(serviceName, serviceHost, servicePort, numTxn, 
					new Transaction[]{new MWTxn(), new AllRMTxn()}, sleepTime, sleepVariance);
			threads[i] = new Thread(clients[i]);
		}
		
		for(Thread t : threads){
			t.start();
			try {
				Thread.sleep((int)((double)sleepTime/numClients)/MS_PER_S);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		for(Thread t : threads){
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		double totMWAvgTimes = 0.0;
		double totRMAvgTimes = 0.0;
		for(TestClient tc : clients){
			double[] avgTimes = tc.getAverageResponseTimes();
			totMWAvgTimes += avgTimes[0];
			totRMAvgTimes += avgTimes[1];
		}
		System.out.println("Average MW-only response time: " + Double.toString(totMWAvgTimes/numClients));
		System.out.println("Average RM-only response time: " + Double.toString(totRMAvgTimes/numClients));
	}
	
	public static void main(String[] args){
		// params
		if (args.length != 3) {
            System.out.println("Usage: MyClient <service-name> " 
                    + "<service-host> <service-port>");
            System.exit(-1);
        }
        String serviceName = args[0];
        String serviceHost = args[1];
        int servicePort = Integer.parseInt(args[2]);
        System.out.println("Part (a): Single client experiment: ");
        runExperiment(serviceName, serviceHost, servicePort, 1, 0, 0);
        System.out.println();
        System.out.println("Part (b): Multi-client experiment: ");
    

        System.out.println("TPS: 250-5000-250 (50)");
        runExperiment(serviceName, serviceHost, servicePort, 250, 5000, 250);
        System.out.println();
/*		
        System.out.println("TPS: 250-5000-250 (100)");
        runExperiment(serviceName, serviceHost, servicePort, 250, 2500, 250);
        System.out.println();
        
        System.out.println("TPS: 250-1000-250 (250)");
        runExperiment(serviceName, serviceHost, servicePort, 250, 1000, 250);
        System.out.println();
		
        System.out.println("TPS: 250-5000-250 (500)");
        runExperiment(serviceName, serviceHost, servicePort, 250, 500, 250);
        System.out.println();
        
        System.out.println("TPS: 250-100-250 (2500)");
        runExperiment(serviceName, serviceHost, servicePort, 250, 100, 250);
        System.out.println();
*/
        /*
        System.out.println("TPS: ");
        runExperiment(serviceName, serviceHost, servicePort, , , 250);
        System.out.println();
        
        System.out.println("TPS: ");
        runExperiment(serviceName, serviceHost, servicePort, 250, 5000, 250);
        System.out.println();
        
        System.out.println("TPS: ");
        runExperiment(serviceName, serviceHost, servicePort, 250, 5000, 250);
        System.out.println();*/
//        System.out.println("TPS: ");
//        runExperiment(serviceName, serviceHost, servicePort, 50, 100, 5);
//        System.out.println();
//        System.out.println("TPS: ");
//        runExperiment(serviceName, serviceHost, servicePort, 10, 200, 5);
//        System.out.println();
	}
}

abstract class Transaction{
	
	private static final double NS_PER_S = 1000000000.0;
	private int numRuns = 0;
	private long totalResponseTime = 0;
	ResourceManager proxy;
	
	public void setProxy(ResourceManager proxy){
		this.proxy = proxy;
	}
	
	public void run(){
		Long txnStart = System.nanoTime();
		int tid = proxy.start();
		execute(tid);
		proxy.commit(tid);
		Long txnEnd = System.nanoTime();
		totalResponseTime += txnEnd - txnStart;
		numRuns++;
	}
	
	public double getAvgResponseTime(){
		return totalResponseTime/(numRuns*NS_PER_S);
	}
	

	abstract protected void execute(int id);
}

class MWTxn extends Transaction{

	@Override
	protected void execute(int id) {
		proxy.queryCustomerInfo(id, proxy.newCustomer(id));
		proxy.queryCustomerInfo(id, proxy.newCustomer(id));
		proxy.queryCustomerInfo(id, proxy.newCustomer(id));
	}
}

class AllRMTxn extends Transaction{

	@Override
	protected void execute(int id) {
		proxy.addFlight(id, id, 10, 10);
		proxy.queryFlight(id, id);
		String loc = Integer.toString(id);
		proxy.addCars(id, loc, 1, 1);
		proxy.queryCars(id, loc);
		proxy.addRooms(id, loc, 1, 1);
		proxy.queryRooms(id, loc);
	}
	
}
