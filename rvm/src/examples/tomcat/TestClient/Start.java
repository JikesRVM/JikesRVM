package TestClient;

import java.io.*;
import org.apache.tomcat.util.xml.*;

class Start {

    static RequestSet readXmlRequestFile(String file) {
	try {
	    // get XML file describing HTTP requests
	    File f = new File( file );
	    if (! f.exists()) throw new IOException("File not found: " + file);
    

	    // create XML parser
	    XmlMapper xh = new XmlMapper();
	    
	    // rules for Requests
	    xh.addRule("request-set/post-request", xh.objectCreate("TestClient.PostRequest"));
	    xh.addRule("request-set/post-request", xh.setProperties());
	    xh.addRule("request-set/post-request", xh.addChild("addRequest", "TestClient.Request"));
	    xh.addRule("request-set/get-request", xh.objectCreate("TestClient.GetRequest"));
	    xh.addRule("request-set/get-request", xh.setProperties());
	    xh.addRule("request-set/get-request", xh.addChild("addRequest", "TestClient.Request"));

	    // rules for parsing Put fields
	    xh.addRule("request-set/post-request/form-param", xh.methodSetter("addElement", 2, new String[]{"java.lang.String", "java.lang.String"}));
	    xh.addRule("request-set/post-request/form-param", xh.methodParam(0, "name"));
	    xh.addRule("request-set/post-request/form-param", xh.methodParam(1, "value"));
	    
	    
	    // read config file
	    return (RequestSet) xh.readXml( new FileInputStream(f), new RequestSet() );

	} catch (Throwable e) {
	    System.err.println("Cannot read request file");
	    e.printStackTrace();
	    System.exit( -1 );
	}

	return null;
    }

    public static void main(String[] args) {
	RequestSet requests = null;
	int clients = 2;
	boolean random = false;
	int runForSeconds = 60;
	boolean reuseConnections = false;
	int requestCount = -1;

	// process arguments
	for(int i = 0; i < args.length; i++) {

	    // specify request file
	    if (args[i].equals("-requests")) 
		requests = readXmlRequestFile( args[++i] );

	    // dump request file
	    else if (args[i].equals("-show"))
		requests.show();
	    
	    // set number of clients
	    else if (args[i].equals("-clients"))
		clients = new Integer(args[++i]).intValue();

	    // randomly traverse request set;
	    else if (args[i].equals("-random"))
		random = true;

	    // randomly traverse request set;
	    else if (args[i].equals("-reuseConnections"))
		reuseConnections = true;

	    else if (args[i].equals("-time")) {
		requestCount = -1;
		runForSeconds = new Integer(args[++i]).intValue();
	    }

	    else if (args[i].equals("-count")) {
		requestCount = new Integer(args[++i]).intValue();
		runForSeconds = -1;
	    }
	}

	// start desired number of clieants
	Worker[] workers = new Worker[ clients ];
	for (int i = 0; i < clients; i++) {
	    workers[i] = new Worker( requests.enumerator(random, requestCount), reuseConnections );
	    workers[i].start();
	}

	try {

	    if (runForSeconds != -1) {
		Thread.currentThread().sleep( runForSeconds * 1000 );

		for (int i = 0; i < clients; i++) {
		    workers[i].stop( new WorkerTimeUp() );
		}
	    }

	    for (int i = 0; i < clients; i++) {
		workers[i].join();
	    }

	} catch (InterruptedException e) {
	    System.err.println("Timing loop interrupted!");
	    System.exit( -1 );
	}

	long totalBytes = 0;
	long totalLatency = 0;
	int numRequests = 0;
	for (int i = 0; i < clients; i++) {
	    totalBytes += workers[i].totalBytes;
	    totalLatency += workers[i].totalLatency;
	    numRequests += workers[i].numRequests;
	}


    }

}

