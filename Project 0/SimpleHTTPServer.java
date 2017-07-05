public static void main(String[] args){
	
	if(args.length != 1){
		System.out.println("Incorrect number of args");
		return;
	}
	
	int port;
	ServerSocket server;
	Socket client;
	PrintWriter out;
	BufferedReader in;
	
	try{
		port = Integer.parseInt(args[0]);
		server = new ServerSocket(port);
		client = server.accept();
		out = new PrintWriter(client.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(client.getInputStream()));
		/* TODO: When client connects to ServerSocket, spawn a new thread to handle 
		 communication. No reading or writing to or from a Socket should occur in 
		 the class that the ServerSocket is accepting connections in. 
		 
		 In the thread, read a single String from the client, parse it as an 
		 HTTP 0.8 request and send back an appropriate response according to 
		 the HTTP 0.8 protocol below.
		 
		 Once response has been sent, you should flush() your output streams, wait 
		 a quarter second, close down all communication objects and exit thread.*/
		 
		 
	}
	catch(Exception e){
		System.out.println(e);
	}

	
}