/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * Emulate the VAME debugger, connecting as a client to the Jalapeno server
 *     +---------------+
 *     |  VAME debugger| 
 *     |    client     |
 *     +---------------+
 */

import java.net.*;
import java.io.*;
import java.util.*;

class emuVAME {
  static String host; 
  static int port;
  static BufferedReader in;
  static BufferedWriter out;

  public static void main(String[] args) {
    if (args.length!=2)
      System.exit(0);
    host = args[0];
    try {
      port = Integer.parseInt(args[1]);
      Socket jalapenoSocket = new Socket(host, port);
      System.out.println("Connected to server: " + jalapenoSocket.toString());
      out = new BufferedWriter(new OutputStreamWriter(jalapenoSocket.getOutputStream()));
      in = new BufferedReader(new InputStreamReader(jalapenoSocket.getInputStream()));
      
      // try sending some text
      out.write("Hello", 0, 5);
      out.newLine();
      out.flush();
      
      // try to receive something
      while (!in.ready()) {
      }
      String reply = in.readLine();
      System.out.println("Reading from server: " + reply);
     
    } catch (NumberFormatException e) {
      System.out.println("Bad port number: " + args[1]);
      System.exit(0);
    } catch (IOException e) {
      System.out.println("IO error");
      System.exit(0);
    }

  }

}
