/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * This class handles the network I/O for
 * the debugger
 *
 * Created: Fri Jun 30 14:11:47 2000
 *
 * @author Manu Sridharan
 */

import java.net.*;
import java.io.*;
import java.util.*;

public class JDPServer implements JDPCommandInterface
{

  // network stuff
  
  /**
   * socket on which server will listen
   */
  private ServerSocket serverSocket;

  /**
   * socket for listening to client input
   */
  private Socket clientSocket;

  /**
   * for writing output
   */
  private ObjectOutputStream out;

  /**
   * for reading input
   */
  private BufferedReader in;

  /**
   * port the server is running on
   */
  int port;
  
  /**
   * try to open a socket on a given port for listening
   * for client connections
   */
  public JDPServer (int initialPort)
  {
    port = initialPort;
    boolean socketOpened = false;
    while (!socketOpened)
    {
      try
      {
        serverSocket = new ServerSocket(port);
        System.out.println("JDPServer running on port " + port);
        socketOpened = true;
      }
      catch (IOException e)
      {
        if (++port > 65536)
        {
          System.err.println("No available port");
          System.exit(1);
        }
      }
    }
  }
  
  /**
   * accept a client connection
   */
  public void acceptConnection()
  {
    System.out.println("Waiting for connection...");
    try
    {
      clientSocket = serverSocket.accept();
      System.out.println("Connection detected!");
      out = new ObjectOutputStream(clientSocket.getOutputStream());
      in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }
    catch (IOException e)
    {
      System.err.println("Accept failed.");
      System.exit(1);
    }
    
  }

  /**
   * close the client connection 
   */
  public void closeConnection()
  {
    try
    {
      out.close();
      in.close();
      clientSocket.close();
    }
    catch (IOException e)
    {
      System.err.println("Close failed.");
    }
  }

  // stuff for implementing command interface

  /**
   * Empty
   */
  public void writeCommand(String command) {}

  /**
   * current command
   */
  private String cmd = "?";

  /**
   * argument list
   */
  private String args[] = new String[0];

  public void readCommand(OsProcess proc)
  {
    String input = null;
    try
    {
      // read a line from the input stream
      while ((input = in.readLine()) == null);
    }
    catch (IOException e)
    {
      System.err.println("readCommand failed.");
      System.exit(1);
    }
      
    
    // parse the line into words
    String [] words = toArgs(input);
    
    // if nothing entered, keep the last command
    if (words!=null) {
      if (words.length != 0)  {
	cmd  = words[0];
	args = new String[words.length - 1];
	for (int i = 0; i < args.length; ++i)
	  args[i] = words[i+1];
      }
    }
  }

  /**
   * parse a line into an array of words, useful for arguments parsing
   * @param line      a string (command and arguments)
   * @return          an array of words making up the string
   * @see
   */  
  private String[] toArgs(String line) {
    String lineargs[];
    String str = new String(line);
    Vector vec = new Vector();

    for (;;)  {
      while (str.startsWith(" "))
	str = str.substring(1);          // skip blanks
      if (str.length() == 0)
	break;                           // end of string
      int end = str.indexOf(" ");
      if (end == -1) {
	vec.addElement(str);
	str = "";
      }  else  {
	vec.addElement(str.substring(0, end));
	str = str.substring(end);
      }
    }

    // copy the vector into an array
    if (vec.size() != 0)  {
      lineargs = new String[vec.size()];
      for (int i = 0; i < lineargs.length; ++i)
	lineargs[i] = (String)vec.elementAt(i);
      return lineargs;
    }

    return null;
  }  
    
  public String cmd()
  {
    return cmd;
  }

  public String[] args()
  {
    return args;
  }

  /**
   * write an object to the output
   * @param output     the output Object
   */
  public void writeOutput(Object output)
  {
    try
    {
      out.writeObject(output);
    }
    catch (IOException e)
    {
      System.err.println("error writing object " + output);
      System.exit(1);
    }
  }

}// JDPServer


