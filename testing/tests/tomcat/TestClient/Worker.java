/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author Julian Dolby
 */

package TestClient;

import java.util.*;
import HTTPClient.*;

class Worker extends Thread {

    Enumeration requests;
    boolean reuseConnection;

    long totalBytes;
    long totalLatency;
    int numRequests;
    int numVerifiedRequests;

    Worker(Enumeration requests, boolean reuseConnection) {
        this.requests = requests;
        this.reuseConnection = reuseConnection;
    }

    private void dump(byte[] data) {
        System.err.println("Differing output:");
        System.err.println("------------------------------");
        for(int i = 0; i < data.length; i++) System.err.print((char)data[i]);
        System.err.println("------------------------------");
    }
        
    public void run() {
        HTTPConnection con = null;
        try {
            while (requests.hasMoreElements()) {
                Request req = (Request) requests.nextElement();

                long start = System.currentTimeMillis();

                try {
                    if (con == null || !reuseConnection) {
                        con = new HTTPConnection( req.getUrl() );
                        con.setContext( this );
                    }
                } catch (Throwable e) {
                    throw new BadResult( req, "connection failed" );
                }
                
                try {

                    // 1. send request and get response from server
                    byte[] result = req.getActual( con );
                    long end = System.currentTimeMillis();

                    // 2. check response is ok
                    byte[] desired = req.getDesired();

                    if (desired != null) {
                        if (result.length != desired.length) {
                            dump(result);
                            throw new BadResult( req, "output differs" );
                        }

                        for(int i = 0; i < result.length; i++) 
                            if (result[i] != desired[i]) {
                                dump(result);
                                throw new BadResult( req, "output differs" );
                            }
        
                        numVerifiedRequests++;
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
            throw new java.lang.Error("Got bad result: " + e.getMessage());
        } catch (WorkerTimeUp e) {
            if (con != null) con.stop();
        }
    }

}
