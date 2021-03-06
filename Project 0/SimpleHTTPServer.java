/*
Authors: 
Thanassi Natsis
Alexey Smirnov
*/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.util.concurrent.TimeUnit;

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
	
	private Socket client;
	private PrintWriter out;
	private BufferedReader in;
	
	// Constructs a thread for a socket
	public SimpleServerThread(Socket client){
		super("SimpleServerThread");
		this.client = client;
	}

	// Run the thread
	public void run(){
		
		String input;
		String data;
		
		try{
			try{
				out = new PrintWriter(client.getOutputStream(), true);
				in = new BufferedReader(new InputStreamReader(client.getInputStream()));
			}
			catch(Exception e){
				return;
			}
			
			// Timeout in 3 seconds if no input is given
			try{
				client.setSoTimeout(3000);
				input = in.readLine();
			}
			// timeout
			catch(SocketTimeoutException e){
				out.println("408 Request Timeout");
				out.println();
				
				out.flush();
				
				sleep(250);
				
				closeObjects();
				return;
			}
			
			String code = processInput(input);
			
			// correctly formatted GET input with correct file name
			if(code.equals("200 OK")){
				data = printResource(input.substring(5));
				if(data == null){
					throw new Exception();
				}
				out.println(code);
				out.println();
				out.println();
				out.println(data);
			}
			//error code
			else{
				out.println(code);
			}
			
			out.println();
			out.flush();
			
			// wait quarter sec before closing thread
			sleep(250);
			
			closeObjects();
			return;
		}
		
		// something in our code broke
		catch(Exception e){
			out.println("500 Internal Error");
			out.println();
			
			out.flush();
			
			sleep(250);
			
			closeObjects();
			return;
		}			
	}
	
	// Reads in single string, parses, and sends back response
	public String processInput(String theInput) throws IOException{
		
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
			
			// Check if file exists
			if(new File("." + inputTokens[1]).isFile()){
				return "200 OK";
			}else{
				return "404 Not Found";
			}
			
		// command other than GET	
		}else{ 
			return "501 Not Implemented";
		}
	}
	
	// Print resource contents in one line
	public String printResource(String path) throws IOException{
		
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			
		   StringBuilder data = new StringBuilder();
		   String line;
		   
		   while ((line = br.readLine()) != null) {
			   data.append(line);
		   }
		   
		   return data.toString();
		}
		catch(Exception e){
			return null;
		}
	}
	
	// sleep for given time in milliseconds
	public void sleep(int time){
		try{
			TimeUnit.MILLISECONDS.sleep(time);
		}
		catch(Exception e){
			return;
		}
	}
	
	// close socket and iostreams
	public void closeObjects(){
		try{
			client.close();
			in.close();
			out.close();
		}catch(Exception e){
			return;
		}
	}
}