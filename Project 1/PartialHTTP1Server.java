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

import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.text.SimpleDateFormat;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class PartialHTTP1Server{
	
	public static void main(String[] args){
		
		// Check if the number of inputs is correct
		if(args.length != 1){
			System.out.println("Incorrect number of args");
			return;
		}
		
		// Accept the port to listen on as args[0], parsed as int
		int port = Integer.parseInt(args[0]);
		
		// 50 simultaneous threads at most; space for no more than 5 threads when idle
		RejectedExecutionHandler handler = new RejectedHandler();
		ThreadPoolExecutor pool = new ThreadPoolExecutor(5, 50, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), handler);
		
		Socket client;
		Runnable worker;
		
		// Construct a ServerSocket that accepts connections on the port specified in the command line argument
		try(ServerSocket server = new ServerSocket(port)){
			while(true){
				// When a client connects to ServerSocket, hand off the created Socket to a Thread that handles communication
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
		
		// Read string from the client, parse it as an HTTP 1.0 request
		
		String[] input = new String[2];
		String data = null;
		
		// Once response is sent, flush the output streams, wait a quarter second, close down communication
		// objects and cleanly exit the communication Thread
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
				System.out.println("HTTP/1.0 408 Request Timeout");
				out.print("HTTP/1.0 408 Request Timeout" + "\r\n");
				out.println();
				
				out.flush();
				
				sleep(250);
				
				closeObjects();
				return;
			}
			
			String code = processInput(input);
			
			// correctly formatted GET input with correct file name
			if(code.equals("200 OK")){
				
				String path = "." + input[0].split(" ")[1];
				System.out.println(path);
				File file = new File(path);
				
				out.print("HTTP/1.0 " + code + "\r\n");
				System.out.println("HTTP/1.0 " + code);
				
				String contentType = "Content-Type: " + getContentType(path);
				out.print(contentType + "\r\n");
				System.out.println(contentType);
				
				String contentLength = "Content-Length: " + getContentLength(file);
				out.print(contentLength + "\r\n");
				System.out.println(contentLength);
				
				String lastModified = "Last-Modified: " + getLastModified(file);
				out.print(lastModified + "\r\n");
				System.out.println(lastModified);
				
				String contentEncoding = "Content-Encoding: " + getContentEncoding();
				out.print(contentEncoding + "\r\n");
				System.out.println(contentEncoding);
				
				String allow = "Allow: " + getAllow();
				out.print(allow + "\r\n");
				System.out.println(allow);
				
				String expire = "Expires: " + getExpires();
				out.print(expire + "\r\n");
				System.out.println(expire);
				
				
				out.print("\r\n");
				//out.println();
				System.out.println();
				
				if(!input[0].split(" ")[0].equals("HEAD")){
					data = getContent(path);
					out.print(data + "\r\n");					
				}

			}
			else if(code.equals("304 Not Modified")){				
				out.print("HTTP/1.0 " + code + "\r\n");
				System.out.println("HTTP/1.0 " + code);
				out.print("Expires: " + getExpires() + "\r\n");
				System.out.println("Expires: " + getExpires());
			}
			//error code
			else{
				System.out.println("HTTP/1.0 " + code);
				out.print("HTTP/1.0 " + code + "\r\n");
			}
			
			//out.println();
			System.out.println();
			out.flush();
			
			// wait quarter sec before closing thread
			sleep(250);
			
			closeObjects();
			return;
		}
		
		// something in our code broke
		catch(Exception e){
			out.println("HTTP/1.0 500 Internal Error");
			System.out.println("HTTP/1.0 500 Internal Error");
			out.println();
			System.out.println();
			
			out.flush();
			
			sleep(250);
			
			closeObjects();
			return;
		}			
	}
	
	// Reads in string, parses, and sends back response according to the HTTP 1.0 protocol
	// Supports GET, POST, and HEAD commands
	// Supports MIME types:
	// text/(html and plain)
	// image/(gif, jpeg and png)
	// application/(octet-stream, pdf, x-gzip, zip)
	public String processInput(String[] input) throws IOException{
		
		// blank input
		if(input == null || input[0] == null){
			return "400 Bad Request";
		}
		
		String date = null;
		
		if(input[1] != null && !input[1].isEmpty()){
			if(input[1].length() > 18 && input[1].substring(0,19).equals("If-Modified-Since: ")){
				date = input[1].substring(19);
				System.out.println(date.toString());
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
		
		// if version number is greater than 1.0, then the version is higher than what we support
		if(firstDigit > 1 || (firstDigit == 1 && secondDigit > 0)){
			return "505 HTTP Version Not Supported";
		}
		
		Date headerTime = null, fileTime = null;
		
		// GET, POST, or HEAD are implemented
		File file;
		switch(inputTokens[0]){
			case "GET":					
			case "POST":
				file = new File("." + inputTokens[1]);
			
				if(date != null){
					try{
						ZonedDateTime zdt = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME);
						headerTime = Date.from(zdt.toInstant());
					}catch(DateTimeParseException e){
						headerTime = null;
					}
					fileTime = new Date(file.lastModified());
					SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
					formatter.setTimeZone(TimeZone.getTimeZone("EDT"));
					formatter.format(fileTime);
				}
				
				if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite()){
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
			case "HEAD": 
				file = new File("." + inputTokens[1]);
				
				if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite()){
					
					return "200 OK";
					
				}else if(file.isFile()){
					return "403 Forbidden";
				}
				
				return "404 Not Found";
				
			case "DELETE":
			case "PUT":
			case "LINK":
			case "UNLINK": return "501 Not Implemented";
			default: return "400 Bad Request";
		}
	}
	
	// Print resource contents in one line
	public String getContent(String path) throws IOException{
		
		try{
			byte[] data = Files.readAllBytes(Paths.get(path));
			return new String(data, Charset.defaultCharset());
		}
		catch(Exception e){
			return null;
		}
	}
	
	public String getContentType(String path){
		String mimeType;
		try{
			mimeType = Files.probeContentType(Paths.get(path));
		}
		catch(Exception e){
			return "application/octet-stream";
		}
		
		if(mimeType == null){
			return "application/octet-stream";
		}
		
		switch(mimeType){
			case "text/html": return "text/html";
			case "text/plain": return "text/plain";
			case "image/gif": return "image/gif";
			case "image/jpeg": return "image/jpeg";
			case "image/png": return "image/png";
			case "application/pdf": return "application/pdf";
			case "application/x-gzip": return "application/x-gzip";
			case "application/zip": return "application/zip";
			default: return "application/octet-stream";
		}
	}
	
	public String getContentLength(File file){
		return String.valueOf(file.length());
	}
	
	public String getLastModified(File file){
		
		Date date = new Date(file.lastModified());
		SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
		formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
		
		return formatter.format(date);
	}
	
	public String getContentEncoding(){
		return "identity";
	}
	
	public String getAllow(){
		return "GET, POST, HEAD";
	}
	
	public String getExpires(){
		ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("GMT")).plusDays(1);
		return DateTimeFormatter.RFC_1123_DATE_TIME.format(zdt);
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