/*
 * (C) Copyright IBM 2002
 */
// $Id$

import java.io.*;
import java.net.*;

/**
 * Test that we can set a timeout for accepting a connection on
 * a server socket.
 *
 * @author David Hovemeyer
 */
class TestAcceptTimeout {
    public static void main(String[] argv) {
        try {
            ServerSocket ss;
            ss = new ServerSocket(0);
            ss.setSoTimeout(500); // accept timeout is 500 milliseconds

            // Thread to hang around for 5 seconds.
            // If it returns from the sleep, then the accept did
            // not time out, so the test fails.
            Thread watchDog = new Thread() {
                public void run() {
                    try {
                        Thread.sleep(5000);
                        System.out.println("TestAcceptTimeout FAILURE");
                        System.exit(1);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                        System.exit(1);
                    }
                }
            };
            watchDog.setDaemon(true);
            watchDog.start();

            try {
                ss.accept();
            }
            catch (SocketTimeoutException e) {
                System.out.println("TestAcceptTimeout SUCCESS");
                System.exit(0);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
