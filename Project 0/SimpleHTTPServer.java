import java.net.*;
import java.io.*;
import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
/*
Authors: 
Thanassi Natsis
Alexey Smirnov
*/

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
		// command is GET
		if(inputTokens[0].equals("GET")){
			// no file name given
			if(inputTokens[1] == null){
				return "400 Bad Request";
			}else{
				// Check if file exists
				File resource = new File(inputTokens[1]);
				if(resource.exists()){
					return "200 OK";
				}else{
					return "404 Not Found";
				}
			}
		}else{
			// command other than GET 
			return "501 Not Implemented";
		}
	}
	// Run the thread
	public void run(){
		try(
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		){
			// Timeout in 3 seconds if no input is given
			client.setSoTimeout(3000);
			String input = in.readLine();
			String code = processInput(input);
			out.println(code);
			// correctly formatted GET input with correct file name
			if(code.equals("200 OK")){
				out.println();
				out.println();
				printResource(input.substring(5), out);
			}
			
			out.println();
			out.flush();
			// wait quarter sec before closing thread
			TimeUnit.MILLISECONDS.sleep(250);
			
			client.close();
			
		}
		// timeout
		catch(SocketTimeoutException e){
			out.println("408 Request Timeout");
			out.println();
			
			out.flush();
			
			TimeUnit.MILLISECONDS.sleep(250);
			
			client.close();
		}
		// general error(i hope this works)
		catch(Exception e){
			out.println("500 Internal Error");
			out.println();
			
			out.flush();
			
			TimeUnit.MILLISECONDS.sleep(250);
			
			client.close();
		}			
	}
	// Print resource contents line by line
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