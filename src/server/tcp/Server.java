package server.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
	
	public static void main(String[] args){
		try{
			ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]));
			ResourceManager rm = new ResourceManager();

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

			// run server forever, with each client getting their own thread
			Socket clientSocket;
			Thread clientThread;
			while(true){
				clientSocket = serverSocket.accept();
				clientThread = new Thread(new ConnectionHandler(clientSocket, rm));
				clientThread.start();
			}

		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
