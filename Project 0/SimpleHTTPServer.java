import java.net.*;
import java.io.*;

public class SimpleHTTPServer{
	
	public static void main(String[] args){
		
		if(args.length != 1){
			System.out.println("Incorrect number of args");
			return;
		}
		
		try{
			int port = Integer.parseInt(args[0]);
			ServerSocket server = new ServerSocket(port);
			while(true){
				new SimpleServerThread(server.accept()).start();
			}
		}
		catch(Exception e){
			System.out.println(e);
		}	
	}
}

class SimpleServerThread extends Thread{

	private Socket client = null;
	
	public SimpleServerThread(Socket client){
		
	}
	
}