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
		
	}
	catch(Exception e){
		System.out.println(e);
	}

	
}