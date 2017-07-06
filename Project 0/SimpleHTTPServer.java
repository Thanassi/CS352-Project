import java.net.*;
import java.io.*;
import java.util.*;

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
	public String processInput(String theInput){
		// blank input
		if(theInput == null){
			return "400 Bad Request";
		}
		
		String[] inputTokens = theInput.split(" ");
		// improper formatting
		if(inputTokens.length != 2){
			return "400 Bad Request";
		}
		
		if(inputTokens[0] == "GET"){
			
		}else{
			return "501 Not Implemented";
		}
		// split string into 2 and check inputs for validity
	}
	// Run the thread
	public void run(){
		try(
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		){
			client.setSoTimeout(3000);
			String input = in.readLine();
			String code = processInput(input);
			out.println(code);
			if(code.equals("200 OK")){
				out.println();
				out.println();
				printResource(input.substring(5), out);
			}
			
			out.println();
			out.flush();
			
			TimeUnit.MILLISECONDS.sleep(250);
			
			client.close();
			
		}
		catch(SocketTimeoutException e){
			out.println("408 Request Timeout");
			out.println();
			
			out.flush();
			
			TimeUnit.MILLISECONDS.sleep(250);
			
			client.close();
		}
		catch(Exception e){
			e.printStackTrace();
			
			client.close();
		}			
	}
	
	public void printResource(String path, PrintWriter out){
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
		   String data = "", line = null;
		   while ((line = br.readLine()) != null) {
			   data += line;
		   }
		   out.println(data);
		}
	}
}