/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class is the command line front end to the jdp
 * debugger.  
 *
 * Created: Thu Jun 29 09:43:58 2000
 *
 * @author Manu Sridharan
 * 
 */

public class CommandLineDebugger
{

  /**
   * handles executing the debugger commands
   */
  private Debugger debugger;

  /**
   * handles the command line I/O
   */
  private CommandLine console;

  /**
   * Instance of a command line debugger (outside the JVM)
   * @param   bp           the initial breakpoint where the debugger will stop
   * @param   runner       the program that will load and run the JVM boot image
   * @param   rawMode      flag is true if the terminal is in raw TTY mode
   * @param   interpreted  true if debugger is running through interpreter
   * @param   init_macro   the file of jdp command that will be loaded and executed
   *                       on startup
   * @return  
   * @see     jdp
   */  
  
  public CommandLineDebugger (int bp, String runner, boolean rawMode, boolean interpreted, String init_macro,
                              boolean viewBoot)
  {
    console = new CommandLine("jdp:0>", rawMode);    
    debugger = new Debugger(bp, runner, rawMode, interpreted, init_macro, console, viewBoot);
  }

  /**
   * Open the console for the external debugger and attach to a current user process
   * <p>
   * BootImageWriter.  A process is created to execute the debuggee program.
   * </p>
   * @param processID the process ID to attach to
   * @return  
   * @see     jdp  
   */  
  public void runAttached(int processID, String args[])
  {
    // attach debugger to process
    debugger.attach(processID, args);

    // Loop to read console command
    CommandLoop();

    // exit and detach debugger
    debugger.exitAttached();
    console.writeOutput("Debugger detached.");
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
    // initialize debugger
    debugger.init(args);

    // Loop to read console command
    CommandLoop();

    // exiting debugger
    debugger.exit();
    console.writeOutput("Debugger exits");
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
      // if the process has exited, clean up
      if (debugger.checkCleanup())
      {
        console.setPrompt("jdp>");
      }
      else
      {
        // update the thread number in the command prompt 
        //in case we have switched thread 
        console.setPrompt("jdp:" + debugger.getThreadNumber() + ">");
      }
    }
  }
  


  
}// CommandLineDebugger
