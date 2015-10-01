package client.tcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
	public static void main(String args[]) throws Exception{
		
		String hostname = args[0];
		int port = Integer.parseInt(args[1]);
		
		Socket server = new Socket(hostname, port);

		BufferedReader input = new BufferedReader(new InputStreamReader(server.getInputStream()));
		PrintWriter output = new PrintWriter(new OutputStreamWriter(server.getOutputStream()));

		output.println("HelloWorld");
		server.close();
	}
}
