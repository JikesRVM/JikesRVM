/*
 * (C) Copyright IBM 2002
 */
// $Id$

import java.io.*;
import java.net.*;

/**
 * Test that we can set a timeout on receiving data from a socket
 * input stream.
 * 
 * @author David Hovemeyer
 */

public class TestReceiveTimeout implements Runnable {

    static void failure(Exception e) {
        if (e != null)
            e.printStackTrace();
        System.out.println("TestReceiveTimeout FAILURE");
        System.exit(1);
    }

    static void failure() { failure(null); }

    static void success() {
        System.out.println("TestReceiveTimeout SUCCESS");
        System.exit(0);
    }

    Object lock = new Object();
    boolean havePort = false;
    int port;

    public void run() {
        try {
            Thread server = new Thread() {
                public void run() {
                    try {
                        ServerSocket ss = new ServerSocket(0);
                        synchronized (lock) {
                            port = ss.getLocalPort();
                            havePort = true;
                            lock.notifyAll();
                        }

                        // accept a connection
                        Socket clientConn = ss.accept();
                        System.out.println("Server: Got a connection");

                        // Now we do absolutely nothing with the connection
                    } catch (Exception e) {
                        failure(e);
                    }
                }
            };

            Thread client = new Thread() {
                public void run() {
                    try {
                        synchronized (lock) {
                            while (!havePort)
                                lock.wait();
                        }
                        Socket conn = new Socket("127.0.0.1", port);
                        System.out.println("Client: Connected to server");

                        // Set receive timeout to 50 milliseconds
                        conn.setSoTimeout(50);

                        InputStream in = conn.getInputStream();
                        byte[] buf = new byte[256];

                        try {
                            int n = in.read(buf);

                            failure(); // shouldn't get here
                        }
                        catch (SocketTimeoutException e) {
                            success();
                        }
                    }
                    catch (Exception e) {
                        failure(e);
                    }
                }
            };

            // Thread to wait around for 5 seconds.
            // The client thread's receive should have timed out
            // by then, so if we return from the sleep, then the
            // timeout probably isn't working.
            Thread watchDog = new Thread() {
                public void run() {
                    try {
                        Thread.sleep(5000);
                        failure();
                    }
                    catch (Exception e) {
                        failure(e);
                    }
                }
            };

            server.start();
            client.start();
            watchDog.start();
        }
        catch (Exception e) {
            failure(e);
        }
    }

    public static void main(String[] argv) {
        new TestReceiveTimeout().run();
    }
}
