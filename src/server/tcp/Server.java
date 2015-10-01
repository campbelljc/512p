package server.tcp;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
	
	public static void main(String[] args) throws Exception{
		int port = Integer.parseInt(args[0]);

		ServerSocket serverSocket = new ServerSocket(port);
		
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
		
		while(true){
			Socket clientSocket = serverSocket.accept();
			new ConnectionHandler(clientSocket).run();
		}
	}
}
