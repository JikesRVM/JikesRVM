/*
 * (C) Copyright IBM Corp. 2001
 */

/**
 * This class provides the interface for running
 * the debugger as a server
 *
 * Created: Fri Jun 30 13:32:56 2000
 *
 * @author Manu Sridharan
 * 
 */

public class NetworkDebugger
{
  /**
   * handles executing the debugger commands
   */
  private Debugger debugger;

  /**
   * handles the network I/O
   */
  private JDPServer jdpServer;

  private final static String COMMAND_DONE = "______Command_executed______";

  private final static String PROCESS_TERMINATED = "______Process_terminated______";

  /**
   * Instance of a network debugger (outside the JVM)
   * @param   bp           the initial breakpoint where the debugger will stop
   * @param   runner       the program that will load and run the JVM boot image
   * @param   rawMode      flag is true if the terminal is in raw TTY mode
   * @param   interpreted  true if debugger is running through interpreter
   * @param   init_macro   the file of jdp command that will be loaded and executed
   *                       on startup
   * @return  
   * @see     jdp
   */    
  public NetworkDebugger(int bp, String runner, boolean rawMode, boolean interpreted, String init_macro,
                         boolean viewBoot, boolean dejavu)
  {
    jdpServer = new JDPServer(1024);
    debugger = new Debugger(bp, runner, rawMode, interpreted, init_macro, jdpServer, viewBoot, dejavu);
  }

  /**
   * Open a connection for the external debugger and attach to a current user process
   * <p>
   * BootImageWriter.  A process is created to execute the debuggee program.
   * </p>
   * @param processID the process ID to attach to
   * @return  
   * @see     jdp  
   */  
  public void runAttached(int processID, String args[])
  {
    
    // get a client connection
    jdpServer.acceptConnection();
    
    // attach debugger to process
    debugger.attach(processID, args);

    JDPFileServer fileServer = new JDPFileServer(jdpServer.port + 1, debugger.classpath);
    fileServer.start();
    jdpServer.writeOutput(String.valueOf(fileServer.port));
    jdpServer.writeOutput(NetworkDebugger.COMMAND_DONE);

    // Loop to read console command
    CommandLoop();

    // exit and detach debugger
    debugger.exitAttached();
    jdpServer.writeOutput("Debugger detached.");

    // close the client connection
    fileServer.stop();
    jdpServer.closeConnection();
  }

  /**
   * Initialize the debugger and execute user commands
   * <p>
   * BootImageWriter.  A process is created to execute the debuggee program.
   * </p>
   * @param   args    arguments to be passed to the BootImageWriter 
   *                  (used to be for RunBootImage)
   * @return  
   * @see     jdp  
   */    
  public void run(String args[])
  {
    boolean first = true;
    JDPFileServer fst = null;
    while (true)
    {
      // get a client connection
      jdpServer.acceptConnection();
      
      // initialize debugger
      debugger.init(args);
      // if this is the first connection, initialize
      // the fileserver thread
      if (first)
      {
        fst = new JDPFileServer(jdpServer.port + 1, debugger.classpath);
        first = false;
      }
      fst.start();
      jdpServer.writeOutput(String.valueOf(fst.port));
      jdpServer.writeOutput(NetworkDebugger.COMMAND_DONE);

      // Loop to read console command
      CommandLoop();
      
      // close the client connection      
      fst.stop();
      jdpServer.closeConnection();
    }
  }
  /**
   * Loop to read console command.  The debugger will mostly stay in this loop
   * @param   
   * @return  
   * @see     
   */  
  private void CommandLoop()
  {
    boolean quit = false;

    while (!quit)
    {
      quit = debugger.runCommand();
      if (debugger.checkCleanup() || quit)
      {
        System.out.println("writing process terminated");
        jdpServer.writeOutput(NetworkDebugger.PROCESS_TERMINATED);
      }
      else
      {
        jdpServer.writeOutput(NetworkDebugger.COMMAND_DONE);
      }
      
    }
    
  }  

}// NetworkDebugger

