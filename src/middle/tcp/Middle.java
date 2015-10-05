package middle.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Middle {
	
	public static void main(String[] args){
		try{
			String[] hostnames = new String[] { args[0], args[2], args[4] };
			int[] ports = new int[] { Integer.parseInt(args[1]),  Integer.parseInt(args[3]),  Integer.parseInt(args[5])};

			ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[6]));
			CustomerInfo customerInfo = new CustomerInfo();

			// closes the socket on program shutdown
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						serverSocket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			
			System.out.println("Middleware started");

			// run server forever, with each client getting their own thread
			Socket clientSocket;
			Thread clientThread;
			while(true){
				clientSocket = serverSocket.accept();
				System.out.println("Client connected");
				clientThread = new Thread(new ConnectionHandler(clientSocket, hostnames, ports, customerInfo));
				clientThread.start();
			}

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
