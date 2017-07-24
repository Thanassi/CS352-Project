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

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.ZonedDateTime;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.Date;

public class PartialHTTP1Server{
	
	public static void main(String[] args){
		
		// Check if the number of inputs is correct
		if(args.length != 1){
			System.out.println("Incorrect number of args");
			return;
		}
		
		// Set the connection port for the socket
		int port = Integer.parseInt(args[0]);
		
		RejectedExecutionHandler handler = new RejectedHandler();
		ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 50, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), handler);
		
		Socket client;
		Runnable worker;
		
		// Spawn a new thread to handle communication
		try(ServerSocket server = new ServerSocket(port)){
			while(true){
				client = server.accept();
				worker = new ServerThread(client);
				pool.execute(worker);
			}
		}
		catch(Exception e){
			pool.shutdown();
		}	
	}
}

class ServerThread implements Runnable{
	
	Socket client;
	
	private PrintWriter out;
	private BufferedReader in;
	
	// Constructs a thread for a socket
	public ServerThread(Socket client){
		this.client = client;
	}

	// Run the thread
	public void run(){
		
		String[] input = new String[2];
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
				input[0] = in.readLine();
				
				if(in.ready()){
					input[1] = in.readLine();
				}
			}
			// timeout
			catch(SocketTimeoutException e){
				out.println("HTTP/1.0 408 Request Timeout");
				out.println();
				
				out.flush();
				
				sleep(250);
				
				closeObjects();
				return;
			}
			
			String code = processInput(input);
			
			// correctly formatted GET input with correct file name
			if(code.equals("200 OK")){
				/*
				data = printResource(input[0].substring(5));
				if(data == null){
					throw new Exception();
				}
				*/
				out.println("HTTP/1.0 " + code);
				out.println();
				out.println();
				//out.println(data);
			}
			//error code
			else{
				out.println("HTTP/1.0 " + code);
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
			out.println("HTTP/1.0 500 Internal Error");
			out.println();
			
			out.flush();
			
			sleep(250);
			
			closeObjects();
			return;
		}			
	}
	
	// Reads in single string, parses, and sends back response
	public String processInput(String[] input) throws IOException{
		
		// blank input
		if(input == null || input[0] == null){
			return "400 Bad Request";
		}
		
		String date = null;
		if(input[1] != null && !input[1].isEmpty()){
			if(input[1].length() > 18 && input[1].substring(0,19).equals("If-Modified-Since: ")){
				date = input[1].substring(19);
			}
			else{
				return "400 Bad Request";
			}
		}
		
		String[] inputTokens = input[0].split(" ");
		
		// improper formatting
		if(inputTokens.length != 3){
			return "400 Bad Request";
		}
		
		if(inputTokens[2].length() != 8 || !inputTokens[2].substring(0, 5).equals("HTTP/")
			|| inputTokens[2].charAt(6) != '.' || !Character.isDigit(inputTokens[2].charAt(5))
			|| !Character.isDigit(inputTokens[2].charAt(7))){
			return "400 Bad Request";
		}
		
		int firstDigit = Character.getNumericValue(inputTokens[2].charAt(5));
		int secondDigit = Character.getNumericValue(inputTokens[2].charAt(7));
		
		if(firstDigit > 1 || (firstDigit == 1 && secondDigit > 0)){
			return "505 HTTP Version Not Supported";
		}
		
		Date headerTime = null, fileTime = null;
		
		// command is GET
		switch(inputTokens[0]){
			case "GET":				
				File file = new File("." + inputTokens[1]);
			
				if(date != null){
					try{
						ZonedDateTime zdt = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME);
						headerTime = Date.from(zdt.now().toInstant());
					}catch(DateTimeParseException e){
						headerTime = null;
					}
					fileTime = new Date(file.lastModified());
				}
				
				if(file.isFile() && file.canRead()){
					if(headerTime == null || fileTime == null || headerTime.compareTo(fileTime) < 0){
						return "200 OK";
					}
					else{
						return "304 Not Modified";
					}
				}else if(file.isFile()){
					return "403 Forbidden";
				}
				
				return "404 Not Found";
				
			case "POST": return "200 OK";
			case "HEAD": return "200 OK";
			case "DELETE":
			case "PUT":
			case "LINK":
			case "UNLINK": return "501 Not Implemented";
			default: return "400 Bad Request";
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

class RejectedHandler implements RejectedExecutionHandler{

	@Override
	public void rejectedExecution(Runnable r, ThreadPoolExecutor exec){
		
		ServerThread worker = (ServerThread) r;
		
		try(PrintWriter out = new PrintWriter(worker.client.getOutputStream(), true)){
			
			out.println("HTTP/1.0 503 Service Unavailable");
			out.println();
			
			worker.client.close();
		}
		catch(Exception e){
			return;
		}
	}
	
}