package TestClient;

import java.util.*;
import HTTPClient.*;

class Worker extends Thread {

    Enumeration requests;
    boolean reuseConnection;

    long totalBytes;
    long totalLatency;
    int numRequests;

    Worker(Enumeration requests, boolean reuseConnection) {
	this.requests = requests;
	this.reuseConnection = reuseConnection;
    }

    public void run() {
	HTTPConnection con = null;
	try {
	    while (requests.hasMoreElements()) {
		Request req = (Request) requests.nextElement();

		long start = System.currentTimeMillis();

		try {
		    if (con == null || !reuseConnection)
			con = new HTTPConnection( req.getUrl() );
		} catch (Throwable e) {
		    System.err.println("Bad request: " + req);
		    System.exit( -1 );
		}
		
		try {

		    // 1. send request and get response from server
		    byte[] result = req.getActual( con );
		    long end = System.currentTimeMillis();

		    // 2. check response is ok
		    byte[] desired = req.getDesired();

		    if (desired != null) {
			if (result.length != desired.length)
			    throw new BadResult( req, "output differs" );

			for(int i = 0; i < result.length; i++) 
			    if (result[i] != desired[i])
				throw new BadResult( req, "output differs" );
		    }

		    // 3. record statistics
		    totalBytes += result.length;
		    totalLatency += end - start;
		    numRequests += 1;

		} catch (BadRequest e) {
		    throw new BadResult( req, e.getMessage() );
		}
	    }
	} catch (BadResult e) {
	    System.err.println("Received bad result: " + e.getMessage());
	    System.exit( -1 );
	} catch (WorkerTimeUp e) {
	    if (con != null) con.stop();
	}
    }

}
