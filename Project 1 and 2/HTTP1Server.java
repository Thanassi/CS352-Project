/*
Authors: 
Thanassi Natsis
Alexey Smirnov
Project 2 - implement POST and support invoking server-side code using CGI
*/

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.InetAddress;

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
import java.util.ArrayList;

public class HTTP1Server{
	
	public int gPort;
	public String gIp;
	public static void main(String[] args){
		
		// Check if the number of inputs is correct
		if(args.length != 1){
			System.out.println("Incorrect number of args");
			return;
		}
		
		// Accept the port to listen on as args[0], parsed as int
		int port = Integer.parseInt(args[0]);
		gPort = port;
		
		// get IP from server
		InetAddress ip;
        ip = InetAddress.getLocalHost();
		gIp = ip;
		
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
	private DataOutputStream dOut;
	private OutputStream os;
	
	// Constructs a thread for a socket
	public ServerThread(Socket client){
		this.client = client;
	}

	// Run the thread
	public void run(){
		
		// Read string from the client, parse it as an HTTP 1.0 request
		
		ArrayList<String> input = new ArrayList<>;
		
		// Once response is sent, flush the output streams, wait a quarter second, close down communication
		// objects and cleanly exit the communication Thread
		try{
			try{
				os = client.getOutputStream();
				out = new PrintWriter(os, true);
				in = new BufferedReader(new InputStreamReader(client.getInputStream()));
				dOut = new DataOutputStream(os);
			}
			catch(Exception e){
				return;
			}
			
			// Timeout in 3 seconds if no input is given
			try{
				client.setSoTimeout(3000);
				input.add(in.readLine());
				
				while(in.ready()){
					input.add(in.readLine());
				}
			}
			// timeout
			catch(SocketTimeoutException e){
				out.print("HTTP/1.0 408 Request Timeout" + "\r\n");
				out.print("\r\n");
				
				out.flush();
				
				sleep(250);
				
				closeObjects();
				return;
			}
			
			String code = processInput(input);
			String[] inputTokens = input.get(0).split(" ");
			// correctly formatted GET/HEAD input with correct file name
			if(code.equals("200 OK")){
				String path = "." + input[0].split(" ")[1];
				File file = new File(path);
				out.print("HTTP/1.0 " + code + "\r\n");
				
				// POST statement code - return specified environment variables
				if(inputTokens[0] == "POST"){
					// TODO: Decode the payload according to RFC-3986
					// TODO: Set the CONTENT_LENGTH environment variable to the length of the decoded payload
					String contentLength = "CONTENT_LENGTH: " + getContentLength(file);
					out.print(contentLength + "\r\n");
					
					String scriptName = "SCRIPT_NAME: " + getScriptName(file);
					out.print(scriptName + "\r\n");
					
					String serverName = "SERVER_NAME: " + getServerName(file);
					out.print(serverName + "\r\n");
					
					String serverPort = "SERVER_PORT: " + getServerPort(file);
					out.print(serverPort + "\r\n");
					
					// TODO: Check if post has header "From" or "User-Agent"
					if("From"){
						String httpFrom = "HTTP_FROM: " + getHttpFrom(file);
						out.print(httpFrom + "\r\n");
					}

					if("User-Agent"){
						String httpUserAgent = "HTTP_USER_AGENT: " + getHttpUserAgent(file);
						out.print(httpUserAgent + "\r\n");
					}
					// TODO: Send the decoded payload to the CGI script via STDIN
				}else{
					String contentType = "Content-Type: " + getContentType(path);
					out.print(contentType + "\r\n");
					
					String contentLength = "Content-Length: " + getContentLength(file);
					out.print(contentLength + "\r\n");
					
					String lastModified = "Last-Modified: " + getLastModified(file);
					out.print(lastModified + "\r\n");
					
					String contentEncoding = "Content-Encoding: " + getContentEncoding();
					out.print(contentEncoding + "\r\n");
					
					String allow = "Allow: " + getAllow();
					out.print(allow + "\r\n");
					
					String expire = "Expires: " + getExpires();
					out.print(expire + "\r\n");
					
					out.print("\r\n");
				}	
				//if not head command print contents of file
				if(!input[0].split(" ")[0].equals("HEAD")){
					//if text, print as a string
					if(contentType.substring(14, 18).equals("text")){
						out.print(getTextContent(path) + "\r\n");
					}
					else{
						//if not text, send bytes to client
						byte[] byteData = getByteContent(path);
						for(int i = 0; i < byteData.length; i++){
							out.print(byteData[i]);
						}
						out.print("\r\n");
					}
				}

			}
			else if(code.equals("304 Not Modified")){				
				out.print("HTTP/1.0 " + code + "\r\n");
				out.print("Expires: " + getExpires() + "\r\n");
				out.print("\r\n");
			}
			//error code
			else{
				out.print("HTTP/1.0 " + code + "\r\n");
				out.print("\r\n");
			}
			
			out.flush();
			
			// wait quarter sec before closing thread
			sleep(250);
			
			closeObjects();
			return;
		}
		
		// something in our code broke
		catch(Exception e){
			out.println("HTTP/1.0 500 Internal Error\r\n");
			out.print("\r\n");
			
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
	public String processInput(ArrayList<String> input) throws IOException{
		
		// blank input
		if(input == null || input.get(0) == null){
			return "400 Bad Request";
		}
				
		String[] inputTokens = input.get(0).split(" ");
		
		// improper formatting
		if(inputTokens.length != 3){
			return "400 Bad Request";
		}
		
		//parse http version and check formatting
		if(inputTokens[2].length() != 8 || !inputTokens[2].substring(0, 5).equals("HTTP/")
			|| inputTokens[2].charAt(6) != '.' || !Character.isDigit(inputTokens[2].charAt(5))
			|| !Character.isDigit(inputTokens[2].charAt(7))){
			return "400 Bad Request";
		}
		
		//digits for http version
		int firstDigit = Character.getNumericValue(inputTokens[2].charAt(5));
		int secondDigit = Character.getNumericValue(inputTokens[2].charAt(7));
		
		// if version number is greater than 1.0, then the version is higher than what we support
		if(firstDigit > 1 || (firstDigit == 1 && secondDigit > 0)){
			return "505 HTTP Version Not Supported";
		}
		
		String date = null;
		Date headerTime = null, fileTime = null;
		File file;
		
		// GET, POST, or HEAD are implemented
		switch(inputTokens[0]){
			
			// Requests data from a specified resource
			case "GET":	
				
				//find date if included
				if(input.get(1) != null && !input.get(1).isEmpty()){
					if(input.get(1).length() > 18 && input.get(1).substring(0,19).equals("If-Modified-Since: ")){
						date = input.get(1).substring(19);
					}
				}
			
				file = new File("." + inputTokens[1]);
				
				if(date != null){
					try{
						//parse if-modified-since date from request
						ZonedDateTime zdt = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME);
						headerTime = Date.from(zdt.toInstant());
					}catch(DateTimeParseException e){
						headerTime = null;
					}
					//parse date from file lastModified
					fileTime = new Date(file.lastModified());
					SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
					formatter.setTimeZone(TimeZone.getTimeZone("EDT"));
					formatter.format(fileTime);
				}
				//check file permissions
				if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite()){
					//compare modified times if applicable
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
			
			// Submits data to be processed to a specified resource
			case "POST":
				file = new File("." + inputTokens[1]);
				// check if file is CGI script
				String extension = "";
				int i = file.lastIndexOf('.');
				if (i > 0) {
					extension = fileName.substring(i+1);
				}
				if(extension != "cgi"){
					return "405 Method Not Allowed"
				}	
				if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite() && file.canExecute()){
					boolean length = false, type = false;
					for(int i = 1; i < input.size(); i++){
						if(input.get(i).startsWith("Content-Type: ")){
							try{
								Integer.parseInt(input.get(i).substring(14));
								length = true;
							}catch(Exception e){
								return "411 Length Required"
							}
						}
						else if(input.get(i).startsWith("Content-Type: ")){
							type = true;
						}
					}
					if(length == false){
						return "411 Length Required";
					}
					if(type == false){
						return "500 Internal Error"
					}
					
					return "200 OK";
					
				}else if(file.exists() && !file.isDirectory()){
					return "403 Forbidden";
				}
				
				return "404 Not Found";
				
			//ignore any dates for HEAD
			case "HEAD": 
				file = new File("." + inputTokens[1]);
				
				if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite()){
					
					return "200 OK";
					
				}else if(file.isFile()){
					return "403 Forbidden";
				}
				
				return "404 Not Found";
			//recognized commands but not implemented
			case "DELETE":
			case "PUT":
			case "LINK":
			case "UNLINK": return "501 Not Implemented";
			default: return "400 Bad Request";
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
		//not all mimeTypes needed, default to octet-stream
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
	
	public String getScriptName(File file){
		String scriptName = file.path();
		return scriptName;
	}
	
	public String getServerName(File file){
		String serverName = gIp;
		return serverName;
	}
	
	public String getServerPort(File file){
		String serverPort = gPort;
		return serverPort;
	}
	
	public String getHttpFrom(File file){
		return "From";
	}
	
	public String getHttpUserAgent(File file){
		return "User-Agent";
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
	
	//return time 24 hours after current time
	public String getExpires(){
		ZonedDateTime zdt = ZonedDateTime.now(ZoneId.of("GMT")).plusDays(1);
		return DateTimeFormatter.RFC_1123_DATE_TIME.format(zdt);
	}
	
	// return text resource contents as a string
	public String getTextContent(String path) throws IOException{
		
		try{
			byte[] data = Files.readAllBytes(Paths.get(path));
			return new String(data, Charset.defaultCharset());
		}
		catch(Exception e){
			return null;
		}
	}
	
	//return application/image as byte array
	public byte[] getByteContent(String path) throws IOException{
	
		try{
			return Files.readAllBytes(Paths.get(path));
		}
		catch(Exception e){
			return null;
		}
	
	}
	
	//sleep for given time in milliseconds
	public void sleep(int time){
		try{
			TimeUnit.MILLISECONDS.sleep(time);
		}
		catch(Exception e){
			return;
		}
	}
	
	//close socket and iostreams
	public void closeObjects(){
		try{
			client.close();
			in.close();
			out.close();
			dOut.close();
		}catch(Exception e){
			return;
		}
	}
}

//if more than 50 clients attempt to connect at once they are handled here
//503 error code is thrown
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