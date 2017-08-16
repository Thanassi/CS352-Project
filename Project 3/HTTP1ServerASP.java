/*
Authors: 
Thanassi Natsis
Alexey Smirnov
*/

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.lang.Process;
import java.lang.Runtime;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.InetAddress;
import java.net.URLDecoder;

import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.Files;

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

public class HTTP1ServerASP{
    
    public static int gPort;
    public static String gIp;
    
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
        
        try{
            ip = InetAddress.getLocalHost();
        }catch(Exception e){
            System.out.println("unknown host");
            return;
        }
        
        gIp = ip.getHostAddress();
        
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
    private OutputStream os;
    private InputStream is;
    
    private int postLength;
    private String from = null;
    private String userAgent = null;
    private String content;
	private String cookie = null;
    
    // Constructs a thread for a socket
    public ServerThread(Socket client){
        this.client = client;
    }

    // Run the thread
    public void run(){
        
        // Read string from the client, parse it as an HTTP 1.0 request
        
        ArrayList<String> input = new ArrayList<>();
        
        // Once response is sent, flush the output streams, wait a quarter second, close down communication
        // objects and cleanly exit the communication Thread
        try{
            try{
                os = client.getOutputStream();
                out = new PrintWriter(os, true);
                is = client.getInputStream();
                in = new BufferedReader(new InputStreamReader(is));
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
                out.print("HTTP/1.0 408 Request Timeout\r\n\r\n");
                
                out.flush();
                
                sleep(250);
                
                closeObjects();
                return;
            }
            
            String code = processInput(input);
            String[] inputTokens = input.get(0).split(" ");
            
            // correctly formatted GET/HEAD input with correct file name
            if(code.equals("200 OK")){
                String path = "." + input.get(0).split(" ")[1];
                File file = new File(path);
                
                //if not head command print contents of file
                if(inputTokens[0].equals("GET")){
                    getRequest(inputTokens, path, file);
                }
                else if(inputTokens[0].equals("POST")){
                    postRequest(inputTokens, path, file);
                }
                else{
                    headRequest(path, file);
                }
            }
            else if(code.equals("304 Not Modified")){               
                out.print("HTTP/1.0 " + code + "\r\n");
                System.out.print("HTTP/1.0 " + code + "\r\n");
                out.print("Expires: " + getExpires() + "\r\n\r\n");
                System.out.print("Expires: " + getExpires() + "\r\n\r\n");
            }
            //error code
            else{
                out.print("HTTP/1.0 " + code + "\r\n\r\n");
                System.out.print("HTTP/1.0 " + code + "\r\n\r\n");
            }
            
            out.flush();
            
            // wait quarter sec before closing thread
            sleep(250);
            
            closeObjects();
            return;
        }
        
        // something in our code broke
        catch(Exception e){
            out.print("HTTP/1.0 500 Internal Server Error\r\n\r\n");
            
            out.flush();
            
            sleep(250);
            
            closeObjects();
            return;
        }           
    }
    
    // Implemented GET request
    public void getRequest(String[] inputTokens, String path, File file){
        
		//cgi file
		if(inputTokens[1].endsWith(".cgi")){
			//create arraylist to store env args
			ArrayList<String> envList = new ArrayList<>();
			
			envList.add("CONTENT_LENGTH=" + content.length());
			envList.add("SCRIPT_NAME=" + inputTokens[1]);
			envList.add("SERVER_NAME=" + getServerName());
			envList.add("SERVER_PORT=" + getServerPort());
			envList.add("REQUEST_METHOD=POST");
			
			if(from != null){
				envList.add("HTTP_FROM=" + from);
			}
			
			if(userAgent != null){
				envList.add("HTTP_USER_AGENT=" + userAgent);
			}
			
			if(cookie != null){
				envList.add("HTTP_COOKIE=" + cookie);
			}
			
			//convert list to array for exec function
			String[] envArr = envList.toArray(new String[0]);
			String script = "";
			
			// CGI script code
			try{
				Process proc = Runtime.getRuntime().exec(file.getPath(), envArr);
				
				byte[] buf = new byte[1024];
				int count = 0;
				
				OutputStream cgi = proc.getOutputStream();
				// check if content exists
				if(content.length() > 0){
					content = URLDecoder.decode(content, "UTF-8");
					cgi.write(content.getBytes());
					cgi.flush();
					cgi.close();
				}
				
				InputStream cIn = proc.getInputStream();
				while(true){
					count = cIn.read(buf);
					if(count > 0){
						String temp = new String(buf);
						temp = temp.trim();
						script = script + temp;
					}
					else{
						break;
					}
				}
				script.trim();
				cIn.close();
				
				// Check script length
				//send response to client
				if(script.length() > 0){
					out.print("HTTP/1.0 200 OK\r\n"); 
					
					String expire = "Expires: " + getExpires() + "\r\n";
					out.print(expire);
					
					String allow = "Allow: " + getAllow() + "\r\n";
					out.print(allow);
					
					String contentType = "Content-Type: text/html\r\n";
					out.print(contentType);
					
					String contentEncoding = "Content-Encoding: " + getContentEncoding() + "\r\n";
					out.print(contentEncoding);
					
					String contentLength = "Content-Length: " + script.length() + "\r\n";
					out.print(contentLength);
					
					if(script.startsWith("Set-Cookie: ")){
						out.print(script);
					}
					else{
						out.print("\r\n" + script);
					}
				}
				else{
					out.print("HTTP/1.0 204 No Content\r\n\r\n");
				}           
			}
			catch(Exception e){
				return;
			}
			
			
			return;
		}
		
		//not cgi file
		// print out variables
        out.print("HTTP/1.0 200 OK\r\n");
        
        String lastModified = "Last-Modified: " + getLastModified(file) + "\r\n";
        out.print(lastModified);
        
        String expire = "Expires: " + getExpires() + "\r\n";
        out.print(expire);
        
        String allow = "Allow: " + getAllow() + "\r\n";
        out.print(allow);
        
        String contentType = "Content-Type: " + getContentType(path) + "\r\n";
        out.print(contentType);
        
        String contentEncoding = "Content-Encoding: " + getContentEncoding() + "\r\n";
        out.print(contentEncoding);
        
        String contentLength = "Content-Length: " + getContentLength(file) + "\r\n\r\n";
        out.print(contentLength);
        
        //if text, print as a string
        try{
            if(contentType.substring(14, 18).equals("text")){
                out.print(getTextContent(path) + "\r\n");
                System.out.print(getTextContent(path) + "\r\n");
            }
            else{
                out.flush();
                //if not text, send bytes to client
                byte[] byteData = new byte[1024];
                int count = 0;
                
                try(BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))){
                    while(true){
                        count = bis.read(byteData);
                        if(count > 0){
                            os.write(byteData, 0, count);
                            os.flush();
                        }
                        else{
                            break;
                        }
                    }
                }
                
                out.print("\r\n");
                System.out.print("\r\n");
            }
        }
        catch(Exception e){
            return;
        }
    }
    
    //handles POST command
    public void postRequest(String[] inputTokens, String path, File file){
        
        //create arraylist to store env args
        ArrayList<String> envList = new ArrayList<>();
        
        envList.add("CONTENT_LENGTH=" + content.length());
        envList.add("SCRIPT_NAME=" + inputTokens[1]);
        envList.add("SERVER_NAME=" + getServerName());
        envList.add("SERVER_PORT=" + getServerPort());
		envList.add("REQUEST_METHOD=POST");
        
        if(from != null){
            envList.add("HTTP_FROM=" + from);
        }
        
        if(userAgent != null){
            envList.add("HTTP_USER_AGENT=" + userAgent);
        }
		
		if(cookie != null){
			envList.add("HTTP_COOKIE=" + cookie);
		}
        
        //convert list to array for exec function
        String[] envArr = envList.toArray(new String[0]);
        String script = "";
        
        // CGI script code
        try{
            Process proc = Runtime.getRuntime().exec(file.getPath(), envArr);
            
            byte[] buf = new byte[1024];
            int count = 0;
            
            OutputStream cgi = proc.getOutputStream();
            // check if content exists
            if(content.length() > 0){
                content = URLDecoder.decode(content, "UTF-8");
                cgi.write(content.getBytes());
                cgi.flush();
                cgi.close();
            }
            
            InputStream cIn = proc.getInputStream();
            while(true){
                count = cIn.read(buf);
                if(count > 0){
                    String temp = new String(buf);
                    temp = temp.trim();
                    script = script + temp;
                }
                else{
                    break;
                }
            }
            script.trim();
            cIn.close();
            
            // Check script length
            //send response to client
            if(script.length() > 0){
                out.print("HTTP/1.0 200 OK\r\n"); 
                
                String expire = "Expires: " + getExpires() + "\r\n";
                out.print(expire);
                
                String allow = "Allow: " + getAllow() + "\r\n";
                out.print(allow);
                
                String contentType = "Content-Type: text/html\r\n";
                out.print(contentType);
                
                String contentEncoding = "Content-Encoding: " + getContentEncoding() + "\r\n";
                out.print(contentEncoding);
                
                String contentLength = "Content-Length: " + script.length() + "\r\n";
                out.print(contentLength);
                
				if(script.startsWith("Set-Cookie: ")){
					out.print(script);
				}
				else{
					out.print("\r\n" + script);
				}
            }
            else{
                out.print("HTTP/1.0 204 No Content\r\n\r\n");
            }           
        }
        catch(Exception e){
            return;
        }
    }
    
    // Implemented HEAD request
    public void headRequest(String path, File file){
        
        // return variables
        out.print("HTTP/1.0 200 OK\r\n");
        
        String lastModified = "Last-Modified: " + getLastModified(file) + "\r\n";
        out.print(lastModified);
        
        String expire = "Expires: " + getExpires() + "\r\n";
        out.print(expire);
        
        String allow = "Allow: " + getAllow() + "\r\n";
        out.print(allow);
        
        String contentType = "Content-Type: " + getContentType(path) + "\r\n";
        out.print(contentType);
        
        String contentEncoding = "Content-Encoding: " + getContentEncoding() + "\r\n";
        out.print(contentEncoding);
        
        String contentLength = "Content-Length: " + getContentLength(file) + "\r\n\r\n";
        out.print(contentLength);
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
        if(firstDigit > 1 || (firstDigit == 1 && secondDigit > 1)){
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
                
				if(inputTokens[1].endsWith(".cgi")){
					if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite() && file.canExecute()){
                    
						int length = -1;
						boolean type = false;

						for(int i = 1; i < input.size(); i++){
							if(input.get(i).startsWith("Content-Length: ")){
								try{
									length = Integer.parseInt(input.get(i).substring(16));
								}catch(Exception e){
									return "411 Length Required";
								}
							}
							else if(input.get(i).equals("Content-Type: application/x-www-form-urlencoded")){
								type = true;
							}
							else if(input.get(i).startsWith("From: ")){
								from = input.get(i).substring(6);
							}
							else if(input.get(i).startsWith("User-Agent: ")){
								userAgent = input.get(i).substring(12);
							}
							else if(input.get(i).startsWith("Cookie: ")){
								cookie = input.get(i).substring(8);
							}
							else{
								content = input.get(i);
							}
						}
						// implement error codes
						if(length == -1){
							return "411 Length Required";
						}
						else if(length == 0){
							return "204 No Content";
						}
						
						if(type == false){
							return "500 Internal Server Error";
						}
						
						postLength = length;
						
						return "200 OK";
					
					}else if(file.exists() && !file.isDirectory()){
						return "403 Forbidden";
					}
					
					return "404 Not Found";
				}
				
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
                int index = inputTokens[1].lastIndexOf('.');
                if (index > 0) {
                    extension = inputTokens[1].substring(index+1);
                }
                
                if(!extension.equals("cgi")){
                    return "405 Method Not Allowed";
                }
                // check file to make sure it's valid for use          
                if(file.exists() && !file.isDirectory() && file.canRead() && file.canWrite() && file.canExecute()){
                    
                    int length = -1;
                    boolean type = false;

                    for(int i = 1; i < input.size(); i++){
                        if(input.get(i).startsWith("Content-Length: ")){
                            try{
                                length = Integer.parseInt(input.get(i).substring(16));
                            }catch(Exception e){
                                return "411 Length Required";
                            }
                        }
                        else if(input.get(i).equals("Content-Type: application/x-www-form-urlencoded")){
                            type = true;
                        }
                        else if(input.get(i).startsWith("From: ")){
                            from = input.get(i).substring(6);
                        }
                        else if(input.get(i).startsWith("User-Agent: ")){
                            userAgent = input.get(i).substring(12);
                        }
						else if(input.get(i).startsWith("Cookie: ")){
							cookie = input.get(i).substring(8);
						}
                        else{
                            content = input.get(i);
                        }
                    }
                    // implement error codes
                    if(length == -1){
                        return "411 Length Required";
                    }
                    else if(length == 0){
                        return "204 No Content";
                    }
                    
                    if(type == false){
                        return "500 Internal Server Error";
                    }
                    
                    postLength = length;
                    
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
    
    // Pull content type from file path
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
    
    // Find name of script
    public String getScriptName(File file){
        return file.getPath();
    }
    
    // Find server name
    public String getServerName(){
        return HTTP1ServerASP.gIp;
    }
    
    // Find port
    public int getServerPort(){
        return HTTP1ServerASP.gPort;
    }
    
    // Find length of file
    public String getContentLength(File file){
        return String.valueOf(file.length());
    }
    
    // Find last mod date
    public String getLastModified(File file){
        
        Date date = new Date(file.lastModified());
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        
        return formatter.format(date);
    }
    
    // Find identity
    public String getContentEncoding(){
        return "identity";
    }
    
    // Detect if command is implemented
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
            
            out.print("HTTP/1.0 503 Service Unavailable\r\n\r\n");
       
            worker.client.close();
        }
        catch(Exception e){
            return;
        }
    }
    
}