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
			sleep(250);
			
			closeObjects();
		}
		// general error(i hope this works)
		catch(Exception e){
			out.println("500 Internal Error");
			out.println();
			
			out.flush();
			
			sleep(250);
			
			closeObjects();
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
			
		}else{
			// command other than GET 
			return "501 Not Implemented";
		}
	}
	
	// Print resource contents line by line
	public void printResource(String path, PrintWriter out) throws IOException{
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
		   StringBuilder data = new StringBuilder();
		   String line;
		   while ((line = br.readLine()) != null) {
			   data.append(line);
		   }
		   out.println(data);
		}
	}
	
	public void sleep(int time){
		try{
			TimeUnit.MILLISECONDS.sleep(time);
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	public void closeObjects(){
		try{
			client.close();
			in.close();
			out.close();
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}