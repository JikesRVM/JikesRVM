/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * server for sending source files to the client
 *
 *
 * Created: Thu Jul 20 10:22:10 2000
 *
 * @author Manu Sridharan
 * @version
 */

public class JDPFileServer implements Runnable
{
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
  private PrintWriter out;

  /**
   * for reading input
   */
  private BufferedReader in;

  /**
   * classpath
   * for finding source files
   */
  private String classpath;

  /**
   * port file server is listening on
   */
  int port;
   
  /**
   * thread on which server will run
   */
  private volatile Thread serverThread;

  /**
   * try to create server socket
   */
  public JDPFileServer (int initialPort, String cp)
  {
    port = initialPort;
    boolean socketOpened = false;
    while (!socketOpened)
    {
      try
      {
        serverSocket = new ServerSocket(port);
        //System.out.println("JDPFileServer running on port " + port);
        socketOpened = true;
      }
      catch (IOException e)
      {
        if (++port > 65536)
        {
          System.err.println("No available sockets");
          System.exit(1);
        }
      }
    }
    classpath = cp;
  }

  /**
   * start the thread
   */
  public void start()
  {
    if (serverThread == null)
    {
      serverThread = new Thread(this);
      serverThread.start();
    }
  }

  /**
   * stop the thread
   */
  public void stop()
  {
    serverThread = null;
  }

  /**
   * listen for requests and send the
   * relevant source files
   */
  public void run()
  {
    acceptConnection();
    String input = null;
    Thread myThread = Thread.currentThread();
    while (serverThread == myThread) // check to see if server has been stopped
    {
      try
      {
        input = in.readLine();
        if (input != null)
        {
          sendSourceFile(input);
        }
      }
      catch (IOException e)
      {
        System.out.println("JDPFileServer: read failed");
        System.exit(1);
      }
    }
    closeConnection();

  }

  /**
   * sends the source file corresponding to
   * the provided class name
   * @param className name of relevant class
   */
  private void sendSourceFile(String className)
  {
    String fileInput = null;    
    File sourceFile = findSourceFile(className);
    if (sourceFile == null) // couldn't find file
    {
      out.println("___File_not_found___");
    }
    else
    {
      // send the file contents to the output
      out.println("___File_Start___");
      BufferedReader fileReader = null;
      try
      {
        fileReader = new BufferedReader(new FileReader(sourceFile));
      }
      catch (FileNotFoundException e) {}
      try
      {
        while ((fileInput = fileReader.readLine()) != null)
        {
          out.println(fileInput);
        }
      }
      catch (IOException e) {}
      out.println("___File_End___");
    }
  }

  /**
   * accept a client connection
   */
  public void acceptConnection()
  {
    try
    {
      clientSocket = serverSocket.accept();
      out = new PrintWriter(clientSocket.getOutputStream(), true);
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
  

  /**
   * finds the full path for a source file running
   * in RVM given the classname, won't work if
   * source file is not in classpath
   * @param className name of relevant class
   * @return File object corresponding to source file, or null if file not found
   */
  private File findSourceFile(String className)
  {
    String fileName = className.replace('.', File.separatorChar) + ".java";
    Vector paths = parseClasspath(classpath);
    for (int i = 0, n = paths.size(); i < n; i++)
    {
      String fullFileName = (String)paths.elementAt(i) + File.separator + fileName;
      //System.out.println(fullFileName);
      File file = new File(fullFileName);
      if (file.exists())
      {
        return file;
      }
    }
    return null;
  }

  /**
   * parse a String representing the classpath into
   * a vector of paths
   * @param classpath String representation of classpath
   * @return a Vector of paths
   */
  private Vector parseClasspath(String classpath)
  {
    Vector v = new Vector();
    StringTokenizer st = new StringTokenizer(classpath, System.getProperty("path.separator"), false);
    while (st.hasMoreTokens())
    {
      v.addElement(st.nextToken());
    }
    return v;
  }  
  
}// JDPFileServer
