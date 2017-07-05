import java.net.*;
import java.io.*;

/* TODO: When client connects to ServerSocket, spawn a new thread to handle 
		 communication. No reading or writing to or from a Socket should occur in 
		 the class that the ServerSocket is accepting connections in. 
		 
		 In the thread, read a single String from the client, parse it as an 
		 HTTP 0.8 request and send back an appropriate response according to 
		 the HTTP 0.8 protocol below.
		 
		 Once response has been sent, you should flush() your output streams, wait 
		 a quarter second, close down all communication objects and exit thread.*/

public class SimpleHTTPServer{
	
	public static void main(String[] args){
		
		if(args.length != 1){
			System.out.println("Incorrect number of args");
			return;
		}
		
		int port = Integer.parseInt(args[0]);
		
		try(ServerSocket server = new ServerSocket(port)){
			while(true){
				new SimpleServerThread(server.accept()).start();
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}	
	}
}

class SimpleServerThread extends Thread{

	private Socket client = null;
	
	public SimpleServerThread(Socket client){
		super("SimpleServerThread");
		this.client = client;
	}

	public void run(){
		try(
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		){
			String input = in.readLine();
			String code = processInput(input);
			out.println(code);
			if(code.equals("200 OK")){
				getResource(input.split(" ")[1]);
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
}