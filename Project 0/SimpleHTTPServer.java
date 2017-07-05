import java.net.*;
import java.io.*;

/* TODO: Once response has been sent, you should flush() your output streams, wait 
		 a quarter second, close down all communication objects and exit thread.*/

public class SimpleHTTPServer{
	
	public static void main(String[] args){
		// Check if the number of inputs is correct
		if(args.length != 1){
			System.out.println("Incorrect number of args");
			return;
		}
		// Set the connection port for the socket
		int port = Integer.parseInt(args[0]);
		
		// Spawn a new thread to handle communication
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
	
	// Constructs a thread for a socket
	public SimpleServerThread(Socket client){
		super("SimpleServerThread");
		this.client = client;
	}
	// Reads in single string, parses, and sends back response
	public String processInput(String input){
		
	}
	// Run the thread
	public void run(){
		try(
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		){
			//do the stuff here
			
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
}
