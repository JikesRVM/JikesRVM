/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * This class is the front end to the jdp debugger.  
 * A user jdp_console is established to accept commands to debug the program 
 * <p>
 * It can be instantiated:
 * <ul>
 * <li> as an external debugger from a command line parser such as jdp.java
 * A new process will be created to run the debuggee program.
 * <li> as an internal debugger from within the JVM such as in the exception 
 * handling method.  
 * </ul>
 * <p>
 * This class was implemented as a thread extension but was changed to a standard
 * object because the current JVM does not support thread yet.
 * @author Ton Ngo 1/15/98
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.reflect.*;

import com.ibm.jikesrvm.jdi.jdwp.*;

// class Debugger extends Thread implements jdpConstants {
public class Debugger implements jdpConstants { //,Runnable /*TODO*/{

  ////////////////////////////////////////////////////////////////////////
  // Member variables
  ////////////////////////////////////////////////////////////////////////

  /**
   * The address to which to listen fro JDI.
   */
  private String address;

  /**
   * Initial breakpoint on startup; the debugger will stop here.
   */
  private int initial_bp = 0;             

  /**
   * Name of initial jdp macro file to execute on startup
   */
  private String initial_macro = null;    

  /**
   * Name of the program to load and run the boot image
   */
  private String bi_runner = null;       

  /**
   * The arguments set up for the boot image runner
   */
  private String bi_args[];

  /**
   * Flag for exiting the command loop
   */
  private boolean quit = false;

  /**
   * The interface to the user jdp_console
   */
  private JDPCommandInterface jdp_console;

  public JDPCommandInterface console() { return jdp_console; }

  /**
   * The process executing the debuggee
   */
  private OsProcess user;

  public OsProcess getUser() { return user; }

  /**
   * Saved argument list for restarting
   */
  private String saved_args[];

  /**
   * Saved bootimage name for restarting
   */
  private String saved_progname;

  /**
   * Saved breakpoints when restarting debuggee
   */
  private breakpointList saved_bpset;

  /**
   * Flag set to true if the previous command was for java source debugging 
   * (e.g. stepline), false if machine code debugging (e.g. step).
   * This allows jdp to intelligently display the current java source line or 
   * machine instruction for certain commands
   */
  private int printMode;              

  /**
   * Flag set to true  
   */
  public static boolean interpretMode = false;

  /**
   * flag to view booting of VM
   */
  private boolean viewBoot;

  /**
   * Macro file holding jdp commands
   */
  private jdpMacro macro;            

  /**
   * Flag to remember what type of debugging environment we got in
   */
  private int debuggerEnvironment;
  private final int EXTERNALCREATE = 1;
  private final int EXTERNALATTACH = 2;
  private final int INTERNAL = 3;

  /**
   * run status of program
   */
  private boolean runstat;

  /**
   * File name for list of classes to be included in the boot image
   * (for BootImageWriter)
   */
  String classesNeededFilename=null;

  /**
   * Java classpath, necessary for the TOC to be loaded in the same 
   * sequence as in the boot image;  otherwise the static field 
   * will be incorrect in the <i>print</i> command
   */
  String classpath=null;

  /**
   * Preference
   */
  static char integerPreference='d';   // print array, class fields in hex or integer
  static char stackPreference='x';     // print stack with or without decimal column
  static char fprPreference='f';     // print FPR values in hex or in float
  static boolean showFPRsPreference = false;  // Show or do not show FPRs with other regs

  /**
   * Table of JDPCommand objects implementing JDP commands.
   */
  JDPCommand[] commandTable;

  /**
   * Current command dictionary mapping command names to debugger commands.
   */
  CommandDictionary commandDictionary;

  /**
   * Hadlesp proxies to the JDWP debugger
   */
  private DebuggerProxy debuggerProxy;

  ////////////////////////////////////////////////////////////////////////

  /**
   * Sets the address to which to listen for JDI.
   * @param adderss the address to which to listen for JDI.
   */
  void setAddress(String address) {
    this.address = address;
  }


  /**
   * Instance of an external debugger (outside the JVM)
   * @param   bp           the initial breakpoint where the debugger will stop
   * @param   runner       the program that will load and run the JVM boot image
   * @param   rawMode      flag is true if the terminal is in raw TTY mode
   * @param   init_macro   the file of jdp command that will be loaded and executed
   *                       on startup
   * @param   jdp_console      handles I/O
   * @return  
   * @see     jdp
   */  
  public Debugger(int bp, String runner, boolean rawMode, boolean interpreted, String init_macro,
                  JDPCommandInterface console, boolean _viewBoot) {
    
    if (debug) {
      System.err.println("bp="+bp);
      System.err.println("runner="+runner);
      System.err.println("rawMode="+rawMode);
      System.err.println("interpreted="+interpreted);
      System.err.println("init_macro="+init_macro);
      System.err.println("console="+console);
      System.err.println("_viewBoot="+_viewBoot);
    }


    debug("*** Platform.init (start) ***");

    // load the JNI library to access ptrace
    Platform.init();

    debug("*** Platform.init  (done) ***");

    // save the initial breakpoint
    // a hack:  we need to stop where the registers have been initialized
    // currently, this is some platform specific number of instructions
    // further from the .bootThread routine
    // normally used as the initial breakpoint
    initial_bp = bp + Platform.initialbp_offset;
    if (init_macro!=null)
      initial_macro = init_macro+".jdp";
    else 
      initial_macro = null;
    bi_runner = runner;
    jdp_console = console;
    printMode = PRINTASSEMBLY;

    interpretMode = interpreted;
    viewBoot = _viewBoot;

    // default:  out of process debugger
    if (runner==null)
      debuggerEnvironment = EXTERNALATTACH;         
    else
      debuggerEnvironment = EXTERNALCREATE; 

    macro = new jdpMacro();

    commandTable = buildCommandTable();
    commandDictionary = CommandDictionary.getStandardDictionary();
  }

  /**
   * Initialize the debugger to listen in on the given
   * host and port
   *
   * @param host host on which to listen
   * @param port port on which to listen
   */
  JDWP jdwp;
  JDWP listen() { return listen(address); }
  JDWP listen(String address) {
    try {
      debuggerProxy = new DebuggerProxy(user, this);
      jdwp = new JDWP(debuggerProxy);
      jdwp.listen(address);
    } catch (Throwable t) {
      System.err.println("Trouble listening to " + address);
      t.printStackTrace();
      System.err.println("------------------------------");
    }
    return jdwp;
  }

//    private void isMethods(Socket socket, String msg) throws Exception {
//      Method[] ms = Socket.class.getMethods();
//      System.out.println("******************** Is Methods ( " + msg + " ) ********************");
//      for (int i = 0; i < ms.length; i++) {
//        Method m = ms[i];
//        if (m.getName().startsWith("is") && m.getReturnType().equals(boolean.class)) {
//  	System.out.println(" -> " + m.getName() + " = " + m.invoke(socket, new Object[]{}));
//        }
//      }
//      System.out.println("*****************************************************");
//    }
  
//    private void doListen(String address) throws Exception {
//      if (debug) System.out.println("*** listen (start) ***");

//      // Sanity checks
//      if (address == null) throw new NullPointerException("adderss cannot be null!");
//      if (address.length() == 0) throw new IllegalArgumentException("address must have size > 0!");

//      // Find the host and port
//      int icolon = address.indexOf(":");
//      final String hostString, portString;
//      if (icolon == -1) {
//        hostString = "localhost";
//        portString = address;
//      } else {
//        hostString = address.substring(0,icolon);
//        portString = address.substring(icolon+1);
//      }

//      // We'll read from the address host:port
//      final String host = hostString;
//      final int port = Integer.decode(portString).intValue();

//      // Set up a thread to read fom the address
//      ServerSocket server = null;
//      Socket socket = null;
//      OutputStream out = null;
//      InputStream in = null;
//      try {
//  //        server = new ServerSocket(port);
//  //        socket = server.accept();
//        class Sacket extends Socket {
//  	Sacket(String s, int p)  throws UnknownHostException, IOException {
//  	  super(s,p);
//  	}
//  	public void close() throws IOException {
//  	  System.out.println(" --------> Closing the fucking socket!");
//  	  new Exception().printStackTrace();
//  	  super.close();
//  	}
//        }
//        socket = new Socket(host, port);

//        isMethods(socket, "initial");

//        if (debug) System.out.println("*** listen socket=" + socket + " ***");
//        in = socket.getInputStream(); isMethods(socket, "after socket.getInputStream");
//        out = socket.getOutputStream(); isMethods(socket, "after socet.getOutputStream");
//        if (debug) System.out.println("*** listen in=" + in + " ***");
//      } catch (Throwable t) {
//        t.printStackTrace(); //TODO
//      } finally {
//  //        if (out != null)    try { out.flush(); }    catch (IOException e) {}
//  //        if (out != null)    try { out.close(); }    catch (IOException e) {}
//  //        if (in != null)     try { in.close(); }     catch (IOException e) {}
//  //        if (socket != null) try { socket.close(); } catch (IOException e) {}
//  //        if (server != null) try { server.close(); } catch (IOException e) {}
//      }

//      if (out == null) throw new RuntimeException("Couldn't create out!");
//      if (in  == null) throw new RuntimeException("Couldn't create in!");


//      final byte[] handshake = "JDWP-Handshake".getBytes();
//      byte[] handshakeInput = new byte[handshake.length];
//      reader = new DataInputStream(in);
//      writer = new DataOutputStream(out);

    
//      Field implField = in.getClass().getDeclaredField("impl");
//      implField.setAccessible(true);
//      SocketImpl impl = (SocketImpl)implField.get(in);

//      Method getFileDescriptor = SocketImpl.class.getDeclaredMethod("getFileDescriptor", new Class[]{});
                                                                  
//      getFileDescriptor.setAccessible(true);
//      Object fd = getFileDescriptor.invoke(impl, new Object[]{});
//      if (debug) System.out.println("*** fd=" + fd + " ***");

//      FileDescriptor fD = new FileDescriptor();
//      Field fdField = SocketImpl.class.getDeclaredField("fd");
//      fdField.setAccessible(true);
//      //fdField.set(impl, fD);

//      fd = getFileDescriptor.invoke(impl, new Object[]{});
//      if (debug) System.out.println("*** fd=" + fd + " ***");

//        reader.readFully(handshakeInput);
//        if (debug) System.out.println("*** read  handshake ***");

//        writer.write(handshake);
//        if (debug) System.out.println("*** wrote handshake ***");

//      String rest = "";
//      rest += "handshake=";
//      for (int i = 0; i < handshake.length; i++) rest += Integer.toHexString(handshake[i]) + " ";
//      rest += "\n";
//      rest += "handshakeInput=";
//      for (int i = 0; i < handshakeInput.length; i++) rest += Integer.toHexString(handshakeInput[i]) + " ";
//      rest += "\n";
//      if (debug) System.out.println("*** handshake " + rest + " ***");
//        if (!Arrays.equals(handshakeInput, handshake)) {
//          String msg = "Handshake arrays are not equal:\n" + rest;
//          throw new RuntimeException(msg);
//        }

    
//      if (debug) System.out.println("*** listen reader=" + reader + " ***");

//      Thread readerThread = new Thread(this);
//      readerThread.start();

//      if (debug) System.out.println("*** listen  (done) ***");
//    }
//    DataInputStream reader = null;
//    DataOutputStream writer = null;
//    public void run() {
//      for (;;) {
//        try {
//  	byte b = (byte)reader.read();
//  	System.out.println("[debugger read] " + b);
//        } catch (Throwable t) {
//  	System.err.println("Trouble reading...");
//  	t.printStackTrace();
//  	break;
//        }
//      }
//    }

  /**
   * Initialize the debugger
   * <p>
   * BootImageWriter.  A process is created to execute the debuggee program.
   * </p>
   * @param   args    arguments to be passed to the BootImageWriter 
   *                  (used to be for RunBootImage)
   * @return  
   * @see     jdp  
   */  
  public void init(String args[]) {

    if (debug) System.out.println("*** init (start) args="+args(args));

    int i, status;
    saved_args = args;
    VM_Method mymethod[];
    VM_LineNumberMap mylinemap;

    parseRunArgs(args);

    // for (int k=0; k<bi_args.length; k++) {
    // 	 jdp_console.writeOutput("boot image arg " + k + " = " + bi_args[k]);
    // }



    // Create the OS (AIX/Lintel) process, wait for it to start up and return
    user = new OsProcessExternal(bi_runner, bi_args, saved_progname, 
				 classesNeededFilename, classpath, this);

    if (address != null) {
      listen(address);
      jdwp.startSpinning();
    }

//      // If someone has asked us to listen to an address, do it
//      if (address != null) {
//        listen(address);
//      }

    // wait for process to be ready, set the initial breakpoint
    // and let the process proceeds there
    setInitialBreakPoint();

    //jdp_console.writeOutput(user.mem.printCurrentInstr());

    // initialize for the name completion 
    // CommandCompletion.init();

    // load startup macro:  look for macro from command line first, 
    // then look for startup.jdp in the current directory
    if (initial_macro!=null) {
      macro.load(initial_macro);      
    } else if ((new File("startup.jdp")).exists()) {
      macro.load("startup.jdp");
    }

    if (debug) System.out.println("*** init (end) ***");
  }

  /**
   * Exit the debugger
   */
  public void exit() {
    // exiting debugger
    if (user!=null) 
      user.pkill();
  }

  public static void debugMarker() {
  }

  /**
   * Attach the debugger to an external process
   * <p>
   * BootImageWriter.  A process is created to execute the debuggee program.
   * </p>
   * @param processID the process ID to attach to
   * @return  
   * @see     jdp  
   */  
  public void attach(int processID, String args[]) {
    
    parseRunArgs(args);
    try {
      user = new OsProcessExternal(processID, saved_progname, 
                                   classesNeededFilename, classpath, this);

      // wait for the attachment to complete
      // cache the JTOC value before referring to other JVM structures
      user.reg.cacheJTOC();
      
      // cache the dictionary pointers if running under the interpreter
      if (interpretMode) {
      	mapVM.cachePointers();
      }
      
      // set up the address tables for the boot image
      // (must do this after cachePointers because in interpreted mode
      // we will need use the dictionary pointers)
      user.bmap.fillBootMethodTable();

      jdp_console.writeOutput(user.mem.printCurrentInstr());
    }
    catch (OsProcessException e) {
    }
  }

  /**
   * detach the debugger and exit
   */
  public void exitAttached() {
    if (user!=null) {
      user.bpset.clearAllBreakpoint();
      user.mdetach();
    }
  }
    
  /**
   * Open the jdp_console for the internal debugger and start executing command
   * <p>
   * This is to be invoked from within the jvm, for instance from the exception
   * handler.  No new process or thread is created; instead, the debugger simply
   * runs on the same stack as the current thread, taking measure to exclude any
   * trace of its own existence.
   * (CommandLoop was not working because getSystemProperty was not yet initialized,
   * but it should be working now)
   * <p>
   * @param   
   * @return  
   * @see     
   */  
  // public void runInternal() {
  //   String cmd;
  //   String cmd_args[];
  // 
  //   jdp_console.writeOutput("Entering internal jvm debugger");
  // 
  //   while (!quit) {
  // 	 jdp_console.readCommand(user);    
  // 	 cmd = jdp_console.cmd();
  // 	 cmd_args = jdp_console.args();
  // 	 
  // 	 if (!cmd.equals("")) {
  // 	   if (cmd.equals("quit") || cmd.equals("q")) {
  // 	     quit = true;
  // 	   } else {
  // 	     try {
  // 	       jdpCommand(cmd, cmd_args);
  // 	     } catch (Exception e) {
  // 	       // jdp_console.writeOutput("ERROR executing jdp command: " + e.getMessage());
  // 	       // e.printStackTrace();
  // 	       // jdp_console.writeOutput("email to jvm-coders or try again . . . ");
  // 	     }
  // 	   }
  // 	 }
  //   }
  // }


  /**
   * Extract the arguments for setting things up:
   * the classesneeded file, classpath, bootimage
   */
  private void parseRunArgs(String args[]) {
    String bootimage=null;
    int i;

    for (i=0; i<args.length; i++) {
      // jdp_console.writeOutput("arg " + i + " = " + args[i]);
      if (args[i].equals("-n")) {
	classesNeededFilename = args[++i];
      } else if (args[i].equals("-classpath")) {
	classpath = args[++i];
      } else if (args[i].equals("-i")) {
        bootimage = args[++i];
      } else {
        break;
      }
    }

    if (classpath==null) {
      jdp_console.writeOutput("ERROR:  no classpath, static fields will be incorrect");
    }
    if (classesNeededFilename==null) {
      jdp_console.writeOutput("ERROR: no classesNeeded file, line number will not be correct");
      return;
    }
    // build the args for the user process, save for later use
    if (bootimage!=null) {
      saved_progname = bootimage;
      bi_args = new String[args.length-i+2];
      int j = 0;
      bi_args[j++] = bi_runner;
      bi_args[j++] = "-X:i="+bootimage;     // no space before the name
      for (int k=i; k<args.length; k++) {
        bi_args[j++] = args[k];
      }
    } else {
      saved_progname = args[args.length-1];
      bi_args = new String[2];
      bi_args[0] = bi_runner;
      bi_args[1] = saved_progname;   // pick the last argument as the boot image
    }
  }

  public boolean runCommand() {

    if (debug) System.err.println("*** runCommand (start) ***");

    String cmd; 
    String cmd_args[];
    // if we are processing a macro file, get the next line
    if (macro.next()) {
      String cmd_arg_string=" ";
      cmd = macro.cmd();
      cmd_args = macro.args();
      for (int i=0; i<cmd_args.length; i++)
      {
        cmd_arg_string += cmd_args[i] + " ";
      }
      jdp_console.writeOutput("\n");
      jdp_console.writeOutput("Macro line " + macro.currentLine() + ": " + 
                              cmd + cmd_arg_string);
    }
    else {
      // otherwise parse the jdp_console input into command and args 
      jdp_console.readCommand(user);     
      cmd = jdp_console.cmd();
      cmd_args = jdp_console.args();
    }
    if (!cmd.equals("")) {    
      if (cmd.equals("quit") || cmd.equals("q")) {
        return true;
      }
      else {
        try {
          return jdpCommand(cmd, cmd_args);
        }
        catch (Exception e) {
          jdp_console.writeOutput("ERROR executing jdp command: " + e.getMessage());
          e.printStackTrace();
          jdp_console.writeOutput("email to jvm-coders or try again . . . ");
        }
      }
    }
    return false;
  }

  // ----------------------------------------------------------------------
  // Communication concerning events from the VM
  // ----------------------------------------------------------------------

  /**
   * Called by OsProcess to tell this that we are at a breakpoint
   * or another stopping point.
   */
  void handleBreakpoint(breakpoint bp) {
    if (debuggerProxy != null) {
      debuggerProxy.handleBreakpoint(bp);
    }
  }

  ////////////////////////////////////////////////////////////////////////
  // JDP command classes
  ////////////////////////////////////////////////////////////////////////

  // TODO: move code of helper methods inside the command classes,
  // so the code is in one place (and not spread out).  This would
  // also make it easier to do many other things, such as
  // properly check argument counts and types, have a UnaryCommand
  // class, emit help messages, etc.

  /**
   * Class representing a JDP command.
   */
  abstract class JDPCommand {
    int minArgs, maxArgs;

    JDPCommand(int minArgs, int maxArgs) {
      this.minArgs = minArgs;
      this.maxArgs = maxArgs;
    }

    int getMinArgs() {
      return minArgs;
    }

    int getMaxArgs() {
      return maxArgs;
    }

    /**
     * Execute the command.
     * @param args Arguments of the command
     * @return true if process should be killed, false if not
     */
    abstract boolean execute(String[] args);

    /**
     * Append short help message for this command to a StringBuffer.
     * This is used in getting help information for all commands.
     */
    abstract void appendShortHelpMessage(StringBuffer buffer);

    /**
     * Append a detailed help message for this command to given StringBuffer.
     */
    abstract void appendDetailedHelpMessage(StringBuffer buffer);
  }

  /**
   * Common base class for JDP commands which do not take arguments.
   */
  abstract class VoidCommand extends JDPCommand {
    VoidCommand() {
      super(0, 0);
    }

    /**
     * Execute the command.
     * @return true if process should be killed, false if not
     */
    abstract boolean execute();

    /** 
     * @see JDPCommand#execute(String[])
     */
    boolean execute(String[] args) {
      return execute();
    }
  }

  /**
   * Command to step to next machine instruction.
   * If the current instruction is a branch, it is followed.
   */
  class StepCommand extends VoidCommand {
    boolean execute() {
      boolean skip_prolog = false;
      printMode = PRINTASSEMBLY;
      runstat = user.pstep(0, printMode, skip_prolog);
      if (runstat==true)
	refreshEnvironment();
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("step          step current thread by instruction, into method\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < s | step > \n");
      ret.append("Single step only the current thread by one machine instruction, \nstepping into all method invocations\n");
    }
  }

  /**
   * Command to step to next machine instruction,
   * stepping over current instruction if it is a branch.
   */
  class StepBrCommand extends VoidCommand {
    boolean execute() {
      if (Platform.stepbrImplemented == 1) {
	printMode = PRINTASSEMBLY;
	runstat = user.pstepOverBranch(0);
	if (runstat==true)
	  refreshEnvironment();
      } else {
	jdp_console.writeOutput("Sorry, step instruction over call is not supported yet on this platform");
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("stepbr        step current thread by instruction, over method\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < sbr | stepbr >\n");
      ret.append("Single step only the current thread by one machine instruction, \nstepping over method invocations\n");
    }
  }

  /**
   * StepLine command.
   * In the RVM user guide, and in the online help, this is described
   * as stepping <em>into</em> a method.  However, the current implementation
   * (as of July 2002) steps <em>over</em> a method.
   */
  class StepLineCommand extends VoidCommand {
    boolean execute() {
      printMode = PRINTSOURCE;
      runstat = user.pstepLine(0, printMode);
      if (runstat==true)
	refreshEnvironment();
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("stepline      step current thread by java source line, into method \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < sl | stepline > \n");
      ret.append("Single step only the current thread by one java source line, stepping into method invocations\n");
      ret.append("(may need to hit enter twice to step one line because currently jdp may not be able to set precise breakpoints)\n");
    }
  }

  /**
   * StepLineOver command.
   */
  class StepLineOverCommand extends VoidCommand {
    boolean execute() {
      printMode = PRINTSOURCE;
      runstat = user.pstepLineOverMethod(0);
      if (runstat==true)
	refreshEnvironment();
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("steplineover  step current thread by java source line, over method \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < slo | steplineover >\n");
      ret.append("Single step only the current thread by one java source line, stepping over method invocations\n");
    }
  }

  /**
   * Command to run a program in the debugger.
   */
  class RunCommand extends JDPCommand {
    RunCommand() {
      super(0, Integer.MAX_VALUE);
    }

    /**
     * Execute a program in the debugger.
     * This is only valid when no child process is running.
     */
    boolean execute(String[] args) {
      switch (args.length) {
      case 0:       // no program name specified, rerun last program
	restart(saved_args);
	break;
      default:
	//jdp_console.writeOutput(args[0] + " " + args[1]);
	//String newargs[] = new String[args.length-1];
	//for (int i=0; i<args.length-1; i++) {
	//  newargs[i] = args[i+1];
	//  jdp_console.writeOutput("args " + newargs[i]);
	//}
	restart(args);
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("run           start new program \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < run > <name ... >\n");
      ret.append("Start a new program\n");
      ret.append("If no program name is specified, rerun the last program\n");
      ret.append("All current breakpoints will be set\n");
      ret.append("The current program must be killed before restarting\n");
    }
  }

  /**
   * Command to kill the process being debugged.
   */
  class KillCommand extends VoidCommand {
    boolean execute() {
      switch (debuggerEnvironment) {
      case EXTERNALCREATE:
	runstat = false;     // to be killed and cleaned up at the end of this method
	break;
      case EXTERNALATTACH:
	jdp_console.writeOutput("Cannot kill attached process, type quit to detach debugger");
	break;
      case INTERNAL:
	jdp_console.writeOutput("Debugger running inside JVM, type quit to exit debugger");
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("kill          terminate program \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < k | kill >\n");
      ret.append("Terminate the current program without exiting the debugger\n");
    }
  }

  /**
   * Command to continue execution of the debugged process.
   */
  class ContinueCommand extends VoidCommand {
    boolean execute() {
      // if there is no breakpoint for an attached process, detach and let it run
      // otherwise we will be stuck with no return
      if (debuggerEnvironment==EXTERNALATTACH && !user.bpset.anyBreakpointExist()) {
	jdp_console.writeOutput("no breakpoint currently set, detaching process");
	return true;
      } else {
	runstat = user.pcontinue(0, printMode, true);
	if (runstat==true)
	  refreshEnvironment();
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("cont          continue all threads\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < c | cont > \n");
      ret.append("Continue all threads, passing to the program any pending signal\n");
    }
  }

  /**
   * Command to continue execution of a single thread.
   */
  class ContinueThreadCommand extends VoidCommand {
    public boolean execute() {
      if (Platform.cthreadImplemented == 1) {
	runstat = user.pcontinue(0, printMode, false);
	if (runstat==true)
	  refreshEnvironment();
      } else {
	jdp_console.writeOutput("Sorry, continue thread is not supported yet on this platform");
      } 
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("cthread       continue current thread only\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < ct | cthread > \n");
      ret.append("Continue only current thread, passing to the program any pending signal\n");
    }
  }

  /**
   * Command to return to caller of current method.
   */
  class ReturnToCallerCommand extends VoidCommand {
    boolean execute() {
      runstat = user.pcontinueToReturn(0, printMode);
      if (runstat==true)
	refreshEnvironment();
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("creturn       continue to last caller \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < cr | creturn >\n");
      ret.append("continue only current thread to the end of this method \n");
      ret.append("(i.e. go up one stack frame)\n");
    }
  }

  /**
   * Command to set thread context.
   */
  class ThreadCommand extends JDPCommand {
    ThreadCommand() {
      super(1, 2);
    }

    boolean execute(String[] args) {
      doThread("thread", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("thread        set or turn off thread context\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < th | thread > <threadID|off>\n");
      ret.append("Select a thread context by its ID \n");
      ret.append("(this is a small integer, get all current thread ID by the listt command)\n");
      ret.append("The new thread context will be shown in the jdp prompt\n");
      ret.append("and all future stack and local display will be for this thread\n");
      ret.append("If no ID is specified, the context is returned to the current thread in which the debugger has stopped\n");
      ret.append("To force jdp to use the context in the hardware register, specify an ID of 0 or OFF; jdp will stay there until the context is set manually to a valid thread ID\n");
      ret.append("jdp will start in the OFF thread (i.e. no thread context)\n");
    }
  }

  /**
   * Base class for read/write register commands.
   * They share a help message.
   */
  abstract class RegisterCommand extends JDPCommand {
    RegisterCommand(int minArgs, int maxArgs) {
      super(minArgs, maxArgs);
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format 1:  < r | reg > <num|name> <count>\n");
      ret.append("Format 2:  < wr | wreg > <num|name> <hexval>\n");
      ret.append("Display/update hardware registers (not thread context registers)\n");
      ret.append("For AIX: you can specify number or name, where number is:  0-31, 128-136, 138, 148, 256-287\n");
      ret.append("For Lintel: you can only specify name.\n");
      ret.append("Display will not include floating point registers unless\n");
      ret.append("'pref showFPRs true' has been specified.\n");
      ret.append("On this plaform the register names are: \n");
      String regname="";
      for (int i=0; i<VM_BaselineConstants.GPR_NAMES.length; i++)
	regname += VM_BaselineConstants.GPR_NAMES[i] + " ";
      ret.append(regname);
      regname = "";
      for (int i=0; i<VM_BaselineConstants.FPR_NAMES.length; i++)
	regname += VM_BaselineConstants.FPR_NAMES[i] + " ";
      ret.append(regname);
      ret.append(Platform.extraRegNames);
    }
  }

  /**
   * Command to read the value of a register.
   */
  class ReadRegisterCommand extends RegisterCommand {
    ReadRegisterCommand() {
       super(0, 2);
    }

    boolean execute(String[] args) {
      doRegisterRead("reg", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("reg           display registers \n");
    }
  }

  /**
   * Command to write the value of a register.
   */
  class WriteRegisterCommand extends RegisterCommand {
    WriteRegisterCommand() {
      super(2, 2);
    }

    boolean execute(String[] args) {
      try {
	int regnum = Integer.parseInt(args[0]);
	int data = parseHex32(args[1]);
	user.reg.write(regnum, data);
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad value for write: " + args[0] + ", " + args[1]);
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("wreg          write register \n");
    }
  }

  /**
   * Command to print the names of registers.
   */
  class RegisterNamesCommand extends VoidCommand {
    boolean execute() {
      doRegisterName("regname");
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("regnames      display register symbolic names \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format: regnames || regnames \n");
      ret.append("Show the correspondence between hardware register names \n");
      ret.append("and symbolic register names \n");
    }
  }

  /**
   * Base class for read/write memory commands which share a
   * detailed help message.
   */
  abstract class ReadOrWriteMemoryCommand extends JDPCommand {
    ReadOrWriteMemoryCommand(int minArgs, int maxArgs) {
      super(minArgs, maxArgs);
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format 1:  < m | mem > <hexaddr> <count>\n");
      ret.append("Format 2:  < wm | wmem > <hexaddr> <hexvalue>\n");
      ret.append("Format 3:  < mraw | memraw > <hexaddr> <hexvalue>\n");
      ret.append("Display/update memory at this address\n");
      ret.append("If count is not specified, 5 words will be displayed\n");
      ret.append("For mem and wmem, the breakpoints are transparent\n");
      ret.append("For memraw, the actual memory contents are shown with the breakpoints as is (intended for debugging jdp)\n");
    }
  }

  /**
   * Command to read actual memory.
   */
  class ReadMemoryRawCommand extends ReadOrWriteMemoryCommand {
    ReadMemoryRawCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doMemoryReadRaw("memraw", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("memraw        display actual memory, jdp breakpoints visible\n");
    }
  }

  /**
   * Command to read memory.
   */
  class ReadMemoryCommand extends ReadOrWriteMemoryCommand {
    ReadMemoryCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doMemoryRead("mem", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("mem           display memory\n");
    }
  }

  /**
   * Command to write memory.
   */
  class WriteMemoryCommand extends ReadOrWriteMemoryCommand {
    WriteMemoryCommand() {
      super(2, 2);
    }

    boolean execute(String[] args) {
      doMemoryWrite("wmem", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("wmem          write memory \n");
    }
  }

  /**
   * Print the value of an object in memory.
   */
  class PrintCommand extends JDPCommand {
    PrintCommand() {
      super(1, 2);
    }

    boolean execute(String[] args) {
      doPrintCommand("print", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("print         print local variables or cast an address as an object\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format 1:   < p | print> frame<:localvar><.field><[n]>\n");
      ret.append("Print the content of a local variable in this stack frame;\n");
      ret.append("If no name is specified, all locals in the current scope are printed\n");
      ret.append("The name can be the string this to print the current object\n");
      ret.append("Example:\n");
      ret.append("   p 0                             print all locals in frame 0\n");
      ret.append("   p this                          print the current object in frame 0\n");
      ret.append("   p 1:mylocal.field1              print this local variable in frame 1\n\n");
      ret.append("Format 2:   < p | print> (classname) hexaddress\n");
      ret.append("Cast the address as an instance of this class\n");
      ret.append("and print the contents \n\n");
      ret.append("Format 3:   < p | print><@class.staticvar>\n");
      ret.append("Print the address of a static variable for this class\n");
    }
  }

  /**
   * Command to print the values of static fields in a class.
   */
  class PrintClassCommand extends JDPCommand {
    PrintClassCommand() {
      super(1, 1);
    }

    boolean execute(String[] args) {
      doPrintClassCommand("printclass", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("printclass    print the class statics or the type of an object address\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format 1:   < pc | printclass> <class><.field><[n]>\n");
      ret.append("Print the static fields for this class\n");
      ret.append("(including super classes up to but not including java.lang.Object)\n");
      ret.append("For array, specify an individual element or omit the rightmost dimension \nto display the full dimension\n");
      ret.append("The variable name can be nested arbitrarily\n");
      ret.append("Example:\n");
      ret.append("   pc class                         print the static variables\n");
      ret.append("   pc class.field                   print this field\n");
      ret.append("   pc class.array[2]                print this array element\n");
      ret.append("   pc class.field1.array[4].field2  nested expression\n\n");
      ret.append("Format 2:   < pc | printclass> <hexaddr>\n");
      ret.append("   pc 01234567                      print the type for this address\n\n");
    }
  }

  /**
   * Common base class for network debugger commands.
   * These have no help available.
   */
  abstract class NetworkDebuggerCommand extends JDPCommand {
    NetworkDebuggerCommand(int minArgs, int maxArgs) {
      super(minArgs, maxArgs);
    }

    void appendShortHelpMessage(StringBuffer ret) {
      // No help available
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("No help available for this command.");
    }
  }

  /**
   * Get representation of given class.
   */
  class GetClassCommand extends NetworkDebuggerCommand {
    GetClassCommand() {
      super(1, 1);
    }

    boolean execute(String[] args) {
      doGetClassCommand("getclass", args);
      return false;
    }
  }

  /**
   * Command to get an object instance (field values and such).
   */
  class GetInstanceCommand extends NetworkDebuggerCommand {
    GetInstanceCommand() {
      super(2, 2);
    }

    boolean execute(String[] args) { 
      doGetInstanceCommand("getinstance", args);
      return false;
    }
  }

  /**
   * Command to get an array instance.
   */
  class GetArrayCommand extends NetworkDebuggerCommand {
    public GetArrayCommand() {
      super(3, 3);
    }

    boolean execute(String[] args) {
      doGetArrayCommand("getarray", args);
      return false;
    }
  }

  /**
   * Command to get class and line number.
   */
  class GetClassAndLineCommand extends NetworkDebuggerCommand {
    GetClassAndLineCommand() {
      super(0, 0);
    }

    boolean execute(String[] args) {
      doGetClassAndLine("getcl", args);
      return false;
    }
  }

  /**
   * Command to get the address of the current instruction.
   */
  class GetCurrentInstrAddrCommand extends NetworkDebuggerCommand {
    GetCurrentInstrAddrCommand() {
      super(0, 0);
    }

    boolean execute(String[] args) {
      doGetCurrentInstrAddr("getcia", args);
      return false;
    }
  }

  /**
   * Command to get current stack frames.
   */
  class GetFramesCommand extends NetworkDebuggerCommand {
    GetFramesCommand() { 
      super(0, 0);
    }

    boolean execute(String[] args) {
      doGetFrames("getframes", args);
      return false;
    }
  }

  /**
   * Command to get locals in current frame.
   */
  class GetLocalsCommand extends NetworkDebuggerCommand {
    GetLocalsCommand() {
      super(4, 4);
    }

    boolean execute(String[] args) {
      doGetLocals("getlocals", args);
      return false;
    }
  }

  /**
   * Command to list machine instructions.
   */
  class ListInstructionsCommand extends JDPCommand {
    ListInstructionsCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doListInstruction("listi", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("listi         list machine instruction\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < li | listi > <hexaddr><count>\n");
      ret.append("Dissassemble the machine instruction in this range of addresses\n");
      ret.append("If address is not specified, the current PC will be used.\n");
      ret.append("If count is specified it must be an integer. It can be negative on PPC\n");
      ret.append("Default count is 10. Count can be specified alone.\n");
    }
  }

  /**
   * Command to list threads.
   */
  class ListThreadsCommand extends JDPCommand {
    ListThreadsCommand() {
      super(0, 1);
    }

    boolean execute(String[] args) {
      doListThread("listt", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("listt         list threads\n\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < lt | listt > <all|byname|run|ready|wakeup|system|gc>\n");
      ret.append("List the threads, select the type of thread by:\n");
      ret.append("  all      all threads listed by top stack frame\n");
      ret.append("  byname   all threads listed by thread class name\n");
      ret.append("  run      threads currently loaded in the system threads\n");
      ret.append("  ready    threads in the VM_Scheduler ready queue\n");
      ret.append("  wakeup   threads in the VM_Scheduler wakeup queue\n");
      ret.append("  system   dump the state of the system threads\n");
      ret.append("  gc       garbage collector threads\n");
      ret.append("Annotation: \n");
      ret.append("  threads loaded in system thread are indicated by >\n");
      ret.append("  the current thread in which the debugger stops is indicated by ->\n");
    }
  }

  /**
   * Command to set a breakpoint.
   */
  class SetBreakpointCommand extends JDPCommand {
    SetBreakpointCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doSetBreakpoint("break", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("break         list/set breakpoint \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < b ><hexaddr><class.method:line sig>\n");
      ret.append("Set breakpoint by hex address or symbolic name \n");
      ret.append("With no argument, the list of current breakpoints is shown\n");
      ret.append("For symbolic name, jdp will attempt to match partial names\n");
      ret.append("The method prolog is skipped;  to break at the start of the prolog, \nspecify 0 for the line number\n");
      ret.append("example:\n");
      ret.append("   b                        list current breakpoints\n");
      ret.append("   b 0123abcd               at this hex address\n");
      ret.append("   b class:line             at java source line in class\n");
      ret.append("   b method                 at start of method, skipping prolog\n");
      ret.append("   b class.method           at start of method, skipping prolog\n");
      ret.append("   b class.method sig       for overloaded method\n");
      ret.append("   b class.method:0         at start of method prolog\n");
      ret.append("the class file must be generated with -g to get the line number\n");
    }
  }

  /**
   * Command to clear a breakpoint.
   */
  class ClearBreakpointCommand extends JDPCommand {
    ClearBreakpointCommand() {
      super(0, 1);
    }

    boolean execute(String[] args) {
      doClearBreakpoint("clearbreak", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("clearbreak    clear breakpoints \n\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < cb ><hexaddr|all> \n");
      ret.append("Clear breakpoint at the hex address or all breakpoint\n");
      ret.append("(type b to get the list of current breakpoints)\n");
      ret.append("If no address is specified, clear breakpoint at the current instruction\n");
    }
  }

  /**
   * Command to print the current stack frame.
   */
  class DisplayCurrentFrameCommand extends JDPCommand {
    DisplayCurrentFrameCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doCurrentFrame("stack", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("stack         display formatted stack \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < f | stack > <hexval> <n>\n");
      ret.append("Display current JVM stack \n");
      ret.append("showing n words at the top and bottom, the default is 4 words\n");
      ret.append("The value for Frame Pointer may be specified in <hexval>\n");
    }
  }

  /** 
   * Print current call stack.
   */
  class ShortStackTraceCommand extends JDPCommand {
    ShortStackTraceCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doShortFrame("where", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("where         print short stack trace \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < w | where > < from | to > <hexval>\n");
      ret.append("Display stack trace\n");
      ret.append("Up to 20 frames are displayed and the number of remaining frames are indicated\n");
      ret.append("Any frame, range of frames, or a specific frame pointer can be specified\n");
      ret.append("If we are in the prolog code, the stack frame is being constructed\n");
      ret.append("so a place holder will be shown for the frame\n");
    }
  }

  /**
   * Display current call stack with full frame information.
   */
  class FullStackTraceCommand extends JDPCommand {
    FullStackTraceCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doFullFrame("whereframe", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("whereframe    print full stack trace \n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < wf | whereframe > < from | to > <hexval>\n");
      ret.append("Display stack trace with arguments, local variables, temporary variables for each stack frame\n");
      ret.append("Up to 20 frames are displayed and the number of remaining frames are indicated\n");
      ret.append("Any frame, range of frames, or a specific frame pointer can be specified\n");
    }
  }

  /**
   * Command to set and display preferences.
   */
  class PreferenceCommand extends JDPCommand {
    PreferenceCommand() {
      super(0, 2);
    }

    boolean execute(String[] args) {
      doSetPreference("preference", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("preference    set user preference\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < preference | pref> <string> <value>\n");
      ret.append("Set user preferences\n");
      ret.append("To display integer in hex or decimal, specify:  int  < hex | x | dec | d > \n");
      ret.append("To display stack with/without a decimal column, specify: stack < hex | x | dec | d > \n");
      ret.append("To display floating point register in hex or float, specify:  fpr  < hex | x | float | f >\n "); 
      ret.append("To select whether floating point registers are displayed as part of reg command, specify: showFPRs true | false\n");
    }
  }

  /**
   * Command to convert hex to decimal.
   */
  class HexToDecCommand extends JDPCommand {
    HexToDecCommand() {
      super(1, 1);
    }

    boolean execute(String[] args) {
      doConvertHexToInt("x2d", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("x2d           convert number from hex to decimal\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  x2d hexnum\n");
      ret.append("Convert number from hex to decimal\n");
    }
  }

  /**
   * Command to convert decimal to hex.
   */
  class DecToHexCommand extends JDPCommand {
    DecToHexCommand() {
      super(1, 1);
    }

    boolean execute(String[] args) {
      doConvertIntToHex("d2x", args);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("d2x           convert number from decimal to hex\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  d2x decimalnum\n");
      ret.append("Convert number from decimal to hex\n");
    }
  }

  /**
   * Command to print thread counts.
   */
  class CountCommand extends VoidCommand {
    boolean execute() {
      doThreadCount(0);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      // no help
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("No help available for this command.");
    }
  }

  /**
   * Command to set thread counts to zero.
   */
  class ZeroCountCommand extends VoidCommand {
    boolean execute() {
      doThreadCount(1);
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      // no help
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("No help available for this command.");
    }
  }

  /**
   * Command to read the contents of a memory location.
   * (Why does JDP have three commands for reading memory?)
   */
  class ReadMemoryAltCommand extends JDPCommand {
    ReadMemoryAltCommand() {
      super(1, 1);
    }

    boolean execute(String[] args) {
      try {
	int addr = parseHex32(args[0]);
	int mydata = user.mem.read(addr);
	jdp_console.writeOutput("true memory = x" + Integer.toHexString(mydata)); 
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad address: " + args[0]);
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      // no help
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("No help available for this command.");
    }
  }

  /**
   * Command to toggle the verbose setting.
   */
  class ToggleVerboseCommand extends VoidCommand {
    boolean execute() {
      if (user.verbose) {
        jdp_console.writeOutput("Verbose now OFF");
        user.verbose = false;
      } else {
        jdp_console.writeOutput("Verbose now ON");
        user.verbose = true;
      }
      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("verbose       toggle verbose mode\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  < v | verbose >\n");
      ret.append("Toggle verbose mode on and off\n");
      ret.append("In verbose, the current stack frame is automatically displayed\n");
    }
  }

  /**
   * Command to print help information.
   */
  class HelpCommand extends JDPCommand {
    HelpCommand() {
      super(0, 1);
    }

    boolean execute(String[] args) {
      StringBuffer ret = new StringBuffer();

      if (args.length == 1) {
	int commandNum = commandDictionary.lookup(args[0]);
	if (commandNum < 0 || commandNum >= commandTable.length)
	  appendCommandSummaries(ret);
	else
	  commandTable[commandNum].appendDetailedHelpMessage(ret);
      }
      else
	appendCommandSummaries(ret);

      jdp_console.writeOutput(ret.toString());

      return false;
    }

    private void appendCommandSummaries(StringBuffer ret) {
      for (int i = 0; i < commandTable.length; ++i) {
        commandTable[i].appendShortHelpMessage(ret);
      }
      ret.append("(macro name)  load and execute this macro (a text file with suffix .jdp)\n");
      ret.append("(enter)       repeat last command\n\n");
      ret.append("To get more information on a specific command including what arguments it can process, type: \n \thelp thiscommand\n");
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("help          print help on all commands or a specific command\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:  help [<command name>]\n");
      ret.append("If no command name is given, a summary of all available commands\n");
      ret.append("is listed.\n");
    }
  }

  /**
   * Command to step to the next source line, stepping into or out of
   * methods if appropriate.  The functionality we are trying
   * to achieve is like what the "step" command in gdb does.
   *
   * <p> Currently, the implementation of this command is rather stupid.
   * It single steps by machine instruction until it looks like the source line
   * is different.  This approach has the advantage of not requiring
   * a breakpoint.
   */
  class StepNextSourceLineCommand extends VoidCommand {
    boolean execute() {

      // Keep single stepping by machine instruction until we find
      // that we're in a different source line (and not executing
      // in the VM).

      // FIXME: this is really, really slow right now.
      // Need to find some common cases that we can optimize.
      // (For example, to a "return to caller" from VM methods).
      // Executing code that we have no source info for is more
      // problematic.

      // FIXME: we should probably just stop if we find ourselves
      // in a method where we have no source info.  Otherwise it
      // could be a very long time before the user gets a jdp
      // prompt again.

      JDP_Frame oldFrame = user.bmap.getBottomFrame();
      boolean haveSourceLocationForOriginalFrame = fillInSourceLocationInformation(oldFrame);
/*
      if (haveSourceLocationForOriginalFrame)
	System.out.println("Starting at: " + oldFrame.getClassName() + ", line " + oldFrame.lineNumber);
*/

      while (/*user.stillRunning()*/ true) {
	// Step to next assembly instruction
	final boolean skip_prolog = false;
	runstat = user.pstep(0, PRINTNONE, skip_prolog);
	if (runstat==true)
	  refreshEnvironment();

	JDP_Frame currentFrame = user.bmap.getBottomFrame();
	if (currentFrame.valid &&
	    fillInSourceLocationInformation(currentFrame)) {

	  //System.out.println("At: " + currentFrame.getClassName() + ", line " + currentFrame.lineNumber);

	  if (!haveSourceLocationForOriginalFrame)
	    // We didn't have source information for the location we started from.
	    // So, just stop here, since now we do know where we are.
	    break;

	  if (!inRemoteVirtualMachine(currentFrame) &&
	      !inSameSourceLine(oldFrame, currentFrame))
	    // Looks like we have reached another source line!
	    break;
	}
      }

      return false;
    }

    void appendShortHelpMessage(StringBuffer ret) {
      ret.append("stepnext      print help on all commands or a specific command\n");
    }

    void appendDetailedHelpMessage(StringBuffer ret) {
      ret.append("Format:   stepnext | sn\n");
      ret.append("Step to next source line, stepping into or out of method as appropriate.\n");
      ret.append("This command tries to emulate the functionality of the \"step\" command\n");
      ret.append("found in gdb.\n");
    }
  }

  /**
   * Retrieve source location information corresponding to given frame.
   * @return true if successful, false if not
   */
  boolean fillInSourceLocationInformation(JDP_Frame frame) {
    // FIXME: this method should throw LnNotAvailException, so
    // caller can handle it.
    try {
      int line = user.bmap.findLineNumber(frame.compiledMethodID, frame.ip);
      frame.lineNumber = line;
      return true;
    }
    catch (LnNotAvailException e) {
      // No line number information available here.
      // Should probably warn the user.
      return false;
    }
    catch (BcPrologException e) {
      // Executing prolog of method, so no line numbers yet.
      // We should hit them eventually.
      return false;
    }
  }

  /**
   * Does it appear that the given frame represents a method
   * in the remote VM (i.e., not the program the remote VM is running?)
   * Assumes that fillInSourceLocationInformation() was successful.
   * Basically, we're just assuming the the user of JDP doesn't care about
   * stepping into VM functions.  Perhaps this should be configurable
   * preference setting.
   */
  boolean inRemoteVirtualMachine(JDP_Frame frame) {
    // Well, this is cheesy, but good enough for now.
    String className = frame.getClassName();
    return className.startsWith("VM_") || className.equals("VM");
  }

  /**
   * Do the given frames appear to represent instructions which are
   * part of the same source line?  Assumes that fillInSourceLocationInformation()
   * was successful.
   */
  boolean inSameSourceLine(JDP_Frame frame1, JDP_Frame frame2) {
    return frame1.getClassName().equals(frame2.getClassName()) &&
	   frame1.lineNumber == frame2.lineNumber;
  }

  /**
   * Build table of JDPCommand objects.
   * This table allows lookup of a Command object by its
   * corresponding enumeration value, specified in
   * <code>jdpConstants</code>.
   */
  private JDPCommand[] buildCommandTable() {
    JDPCommand[] table = new JDPCommand[] {
      // Note: these must be in the same order as specified in jdpConstants
      new StepCommand(),
      new StepBrCommand(),
      new StepLineCommand(),
      new StepLineOverCommand(),
      new RunCommand(),
      new KillCommand(),
      new ContinueCommand(),
      new ContinueThreadCommand(),
      new ReturnToCallerCommand(),
      new ThreadCommand(),
      new ReadRegisterCommand(),
      new WriteRegisterCommand(),
      new RegisterNamesCommand(),
      new ReadMemoryRawCommand(),
      new ReadMemoryCommand(),
      new WriteMemoryCommand(),
      new PrintCommand(),
      new PrintClassCommand(),
      new GetClassCommand(),
      new GetInstanceCommand(),
      new GetArrayCommand(),
      new GetClassAndLineCommand(),
      new GetCurrentInstrAddrCommand(),
      new GetFramesCommand(),
      new GetLocalsCommand(),
      new ListInstructionsCommand(),
      new ListThreadsCommand(),
      new SetBreakpointCommand(),
      new ClearBreakpointCommand(),
      new DisplayCurrentFrameCommand(),
      new ShortStackTraceCommand(),
      new FullStackTraceCommand(),
      new PreferenceCommand(),
      new HexToDecCommand(),
      new DecToHexCommand(),
      new CountCommand(),
      new ZeroCountCommand(),
      new ReadMemoryAltCommand(),
      new ToggleVerboseCommand(),
      new HelpCommand(),
      new StepNextSourceLineCommand(),
    };

    // Sanity check to ensure that the table is the expected size
    if (table.length != NUM_COMMANDS)
      throw new IllegalStateException("command table is out of sync with jdpConstants");

    return table;
  }

  ////////////////////////////////////////////////////////////////////////
  // End of JDP command clases
  ////////////////////////////////////////////////////////////////////////
    
  /**
   * Execute a jdp_console command.  
   * <p>
   * When the program has been started, all jdp 
   * command except <i>run</i> will be accepted. 
   * When the program has exited or has been killed, only the <i>run</i>
   * command will be accepted to restart the program
   * </p>
   * @param   commandName   a jdp command
   * @param   args      argument list for the jdp command
   * @return true if exiting from the debugger
   *         false if continuing to read/process command
   * @see     Debugger.printHelp
   */  
  private boolean jdpCommand(String commandName, String[] args) {

    debug("jdpCommand command=" + commandName + " args=" + args(args));

    // running status of the program, may be set to false by a command in this round
    runstat=true;   
    int addr, count; 

    int commandNum = commandDictionary.lookup(commandName);
    //System.out.println("Got command " + commandNum);
    if (commandNum == UNKNOWN_COMMAND) {
      // Is there a macro of this name?
      if (macro.exists("", commandName+".jdp")) {
	System.out.println("Attempting to load macro: " + commandName +".jdp");
	macro.load(commandName+".jdp");
      }
      else
	jdp_console.writeOutput("Sorry, you've specified an unknown command.\n" +
	  "Please use help to see the list of known commands");
      return false;
    }
    else if (commandNum == AMBIGUOUS_COMMAND) {
      // Command dictionary accepts abbreviations, but the command
      // given could be expanded in more than one way.
      StringBuffer buffer = new StringBuffer();
      buffer.append("Sorry, command \"");
      buffer.append(commandName);
      buffer.append("\" is ambiguous.  Possible completions are:\n\t");
      Iterator i = commandDictionary.getPossibleExpansions().iterator();
      while (i.hasNext()) {
	Map.Entry expansion = (Map.Entry) i.next();
	buffer.append(expansion.getKey());
	buffer.append(' ');
      }
      jdp_console.writeOutput(buffer.toString());
      return false;
    }
    else {
      if (commandNum < 0 || commandNum >= commandTable.length)
	throw new IllegalStateException("Unexpected command number: " + commandNum);

      if (user == null) {
	// If no process is running, then we only accept
	// HELP_COMMAND and RUN_COMMAND.
	if (commandNum != HELP_COMMAND && commandNum != RUN_COMMAND) {
	  jdp_console.writeOutput("No program running, enter:  run ... ");
	  return false;
	}
      }
      else {
	// Don't accept run command if process is already running.
	if (commandNum == KILL_COMMAND) {
	  jdp_console.writeOutput("Debuggee is running, kill before restarting");
	  return false;
	}
      }

      // Find the JDPCommand object
      JDPCommand jdpCommand = commandTable[commandNum];

      // Check that we got the right number of arguments
      int numArgs = args.length;
      if (numArgs < jdpCommand.getMinArgs() || numArgs > jdpCommand.getMaxArgs()) {
	jdp_console.writeOutput("Wrong number of arguments passed to " + commandName + " command.\n" +
	  "Type \"help " + commandName + "\" for more information.\n");
	return false;
      }

      // Now we can actually execute the command
      return jdpCommand.execute(args);
    }
  }

  public boolean checkCleanup() {
    if (!runstat && (user != null)) {
      saved_bpset = user.bpset;
      user.pkill();
      user = null;         /* drop reference, process terminated */
    }
    return !runstat;
  }

  public int getThreadNumber() {
    return user.reg.getContextThreadID();
  }
  
  /**
   * Invoked on any return from the debuggee:
   * the program has executed some code and may have changed its environment
   */
  private void refreshEnvironment() {
    // get the context of the thread we stop in
    // !!!! this may not work yet for intel ... trying it since the code
    // is there
    user.reg.setContextThreadIDFromRun();

    // cache the JTOC value if we stop in a Java stack frame
    user.reg.cacheJTOC();

    // cache the dictionary pointers if running under the interpreter
    if (interpretMode)
      mapVM.cachePointers();
  }


  /**
   */
  private void doTest1(String args[]) {
  }

  /**
   */
  private void doTest(String args[]) {
  }

  /**
   * Print or zero out the thread J2N* counts in table
   * 
   */
  private void doThreadCount(int option) {

    switch (option) {
    case 0: ;
      System.out.println ( user.listThreadsCounts() );
      break;

    case 1: ;
      user.zeroThreadsCounts();
      break;

    default:
      break;
    }

  }  

  /**
   * Wait for a newly created process to be ready, set the initial breakpoint
   * and let the process proceeds there
   */
  private void setInitialBreakPoint() {
    int status;

    if (debug) System.err.println("*** setInitialBreakPoint (start) ***");
    if (debug) System.out.println("*** Setting initial breakpoint at " + Integer.toHexString(initial_bp));

    // don't use pwait or pcontinue yet because the mechanism for jdp and jvm 
    // to handle signal is not set up yet at this point
    status = user.mwait();
    while (user.isIgnoredTrap(status)) {
      if (debug) System.err.println(" setInitialBreakPoint: waiting...");
      user.mcontinue(0);           
      status = user.mwait();
    }

    // If we have an initial breakpoint, set it and proceed there
    // then clear the breakpoint
    if (initial_bp!=0) {
      if (debug) System.out.println("Setting initial breakpoint at " + Integer.toHexString(initial_bp));
      breakpoint bp = new breakpoint(0,0,initial_bp);
      user.bpset.setBreakpoint(bp);    
      user.mcontinue(0);           // continue, ignoring any traps now 
      status = user.mwait();
      // System.out.println("Hitting initial breakpoint");
      while (user.isIgnoredTrap(status)) { // loop until a Breakpoint trap
        user.mcontinue(0);           
        status = user.mwait();
      }
      // System.out.println("Reach initial breakpoint, clearing it");

      user.bpset.clearBreakpoint(bp);    

      // cache the JTOC value before referring to other JVM structures
      if (debug) System.out.println("*** user.reg.cacheJTOC() (start) ***");
      user.reg.cacheJTOC();
      if (debug) System.out.println("*** user.reg.cacheJTOC()  (done) ***");

      // cache the dictionary pointers if running under the interprete

      if (interpretMode) {
	if (debug) System.out.println("*** mapVM.cachePointers() (start) ***");
	mapVM.cachePointers();
	if (debug) System.out.println("*** mapVM.cachePointers()  (done) ***");
      }

      // set up the address tables for the boot image
      // (must do this after cachePointers because in interpreted mod

      // we will need use the dictionary pointers)
      if (debug) System.out.println("*** user.bmap.fillBootMethodTable() (start) ***");
      user.bmap.fillBootMethodTable();
      if (debug) System.out.println("*** user.bmap.fillBootMethodTable()  (done) ***");

      // skip to the user main method if desired
      if (!viewBoot)
      {
	if (debug) System.out.println("*** goToMainMethod() (start) ***");
        goToMainMethod();
	if (debug) System.out.println("*** goToMainMethod()  (done) ***");
      }

    } 
    
    if (debug) System.err.println("*** setInitialBreakPoint  (done) ***");
    
  }

  /**
   * progresses the RVM process to the beginning of the
   * user main() method
   */
  private void goToMainMethod()
  {
    breakpoint bp = null;
    // set a breakpoint at VM.debugBreakpoint(), which is called
    // from MainThread
    try
    {
      bp = user.bmap.findBreakpoint("com.ibm.JikesRVM.VM.debugBreakpoint", null, user.reg.hardwareIP());
      if (debug) System.out.println("*** goToMainMethod bp=" + bp);
    } 
    catch (BmapMultipleException e1)
    {
      jdp_console.writeOutput(e1.getMessage());
    } 
    catch (BmapNotFoundException e2)
    {
      jdp_console.writeOutput(e2.getMessage());
    }
    
    
    if (debug) System.out.println("*** user.bpset.setBreakpoint(bp) (start) ***");
    user.bpset.setBreakpoint(bp);
    if (debug) System.out.println("*** user.bpset.setBreakpoint(bp)  (done) ***");
    
    // Continue
    // We may get spurrious Trace/BPT trap or Seg fault as the system 
    // is inialized (stack resize, etc). Tell the debugger
    // to ignore these during initialization
    if (debug) System.out.println("*** user.enableIgnoreOtherBreakpointTrap();  (start) ***");
    user.enableIgnoreOtherBreakpointTrap();  
    if (debug) System.out.println("*** user.enableIgnoreOtherBreakpointTrap();   (done) ***");

    // !!!! Intel version had PRINTNONE for this call ... don't know why
    if (debug) System.out.println("*** user.pcontinue(0, PRINTASSEMBLY, true) (start) ***");
    user.pcontinue(0, PRINTASSEMBLY, true);
    if (debug) System.out.println("*** user.pcontinue(0, PRINTASSEMBLY, true)  (done) ***");

    if (debug) System.out.println("*** refreshEnvironment() (start) ***");
    refreshEnvironment();
    if (debug) System.out.println("*** refreshEnvironment()  (done) ***");

    if (debug) System.out.println("*** refreshEnvironment() (start) ***");
    refreshEnvironment();
    if (debug) System.out.println("*** refreshEnvironment()  (done) ***");
    
    
    // set breakpoint in main() method of user program
    breakpoint main_bp = setMainBreakpoint();

    if (debug) System.out.println("*** goToMainMethod main_bp="+main_bp);

    // remove original breakpoint
    user.bpset.clearBreakpoint(bp);
    
    // begin catching other trap instructions
    user.disableIgnoreOtherBreakpointTrap();  
    
    // continue to beginning of user's main()
    user.pcontinue(0, PRINTASSEMBLY, true);
    
    if (debug) System.out.println("*** refreshEnvironment() (start) ***");
    refreshEnvironment();
    if (debug) System.out.println("*** refreshEnvironment()  (done) ***");

    // remove the breakpoint at the beginning of the main method
    if (debug) System.out.println("*** user.bpset.clearBreakpoint(main_bp) (start) ***");
    user.bpset.clearBreakpoint(main_bp);
    if (debug) System.out.println("*** user.bpset.clearBreakpoint(main_bp)  (done) ***");
  }


  /**
   * set a breakpoint at the user main() method
   * @return the breakpoint
   */
  private breakpoint setMainBreakpoint()
  {
    JDP_Class mainThread = null;
    // get this, instance of MainThread
    mainThread = user.bmap.currentThreadToJDPClass();
    // get the args field of the MainThread instance
    JDP_Field field = null;
    for (int i = 0; i < mainThread.fields.size(); i++)
    {
      field = (JDP_Field)mainThread.fields.elementAt(i);
      if (field.name.equals("args")) break;
    }
    JDP_Class argsArray = null;
    try
    {
      argsArray = user.bmap.arrayTypeToJDPClass(field.name, field.type,
                                                          field.address,
                                                          false);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    
    // get the 0th element of the args field, the class name
    JDP_Field classNameField = (JDP_Field)argsArray.fields.elementAt(0);
    return setBreakpointAtStringClass(classNameField);
  }

  /**
   * set a breakpoint at the main method of a given class
   * @param stringField a JDP_Field representing the String class name
   * @return the breakpoint
   */
  private breakpoint setBreakpointAtStringClass(JDP_Field stringField)
  {
    JDP_Class stringClass = new JDP_Class();
    stringClass.name = stringField.type;
    stringClass.address = stringField.address;
    try
    {
      user.bmap.classToJDPClass(stringClass.name,
                                stringClass.address,
                                false,
                                stringClass);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    
    // get value field
    JDP_Field valueField = (JDP_Field)stringClass.fields.elementAt(0);
    String charArrayString = valueField.value;
    // get out chars from string format
    // FIXME: this is not a good way to get the name of the main class,
    // because the value has been formatted for display, and may have
    // been truncated.
    if (charArrayString.indexOf("... length is") >= 0)
      throw new Error(
	"You are trying to debug a class whose name is too long for jdp. " +
	"Try increasing MAX_NUM_ARRAY_ELEMENTS in BootMap.java. Sorry!");
    charArrayString = charArrayString.substring(1, charArrayString.indexOf('}'));
    StringTokenizer st = new StringTokenizer(charArrayString, ", ", false);
    StringBuffer ret = new StringBuffer();
    while (st.hasMoreTokens())
    {
      ret.append(st.nextToken());
    }
    String className = ret.toString();
    // set the breakpoint
    breakpoint bp = null;
    try
    {
      bp = user.bmap.findBreakpoint(className + ".main", null, user.reg.hardwareIP());
    } 
    catch (BmapMultipleException e1)
    {
      jdp_console.writeOutput(e1.getMessage());
    } 
    catch (BmapNotFoundException e2)
    {
      jdp_console.writeOutput(e2.getMessage());
    }
    user.bpset.setBreakpoint(bp);
    return bp;
  }

  
  /**
   * Restart the debuggee program, restore any breakpoints currently set
   * @param   args    argument list
   * @return  
   * @see     
   */  
  private void restart(String args[]) {

    // use the same args set up at the beginning for the user process
    user = new OsProcessExternal(bi_runner, bi_args, saved_progname, 
				 classesNeededFilename, classpath , this);

    // wait for process to be ready, set the initial breakpoint
    // and let the process proceeds there
    setInitialBreakPoint();
    
    // then if there are any previous breakpoints saved, set them
    // remember to do this after the initial breakpoint so that 
    // the memory for the boot image has been allocated
    if (saved_bpset.size() > 0) {
      for (int i=0; i<saved_bpset.size(); i++) {
	breakpoint bp = (breakpoint) saved_bpset.elementAt(i);
	if (bp.next_addr!=-1) {
	  // jdp_console.writeOutput("Restoring breakpoint at: x" + Integer.toHexString(bp.next_addr));
	  // jdp_console.writeOutput("... currently: x" + user.mem.read(bp.next_addr));
	  user.bpset.setBreakpoint(bp);
	}
      }
    }
    
  }

  /**
   * Walk the stack to see if we are in the Debugger.  This is necessary
   * in the runtime Exception handler so we don't invoke the Debugger recursively
   * on normal exceptions that are parts of the Debugger
   */
  public boolean calledFromDebugger() {
    // TODO
    return false;
  }

  /**
   * (obsolete)
   * Compile the boot image.  This invokes the PublicizingClassLoader,
   * which runs the BootImageWriter, which pulls in the various classes to
   * create the boot image
   * <p>
   * Convention used for the args on entry to this method:
   * <ul>
   * <li> The first argument is the publicizing class loader
   * <li> The last argument is assumed to be the class name for the boot image
   * </ul>
   * <p>
   * 
   * @param   args  argument list for BootImageWriter
   * @return  
   * @see     BootImageWriter
   */  
  private void compileBootImage(String args[]) {
    String bi_args[] = new String[args.length-1];
    String bi_name = args[args.length-1];
    Class pub_cl;
    Object pub_obj;
    java.lang.reflect.Method pub_methods[];

    jdp_console.writeOutput("Compiling Boot Image for "  + bi_name + " . . . ");
    // jdp_console.writeOutput("invoking " + args[0] + " for this test " + args[args.length-1]);

    // drop the first arg, which should be the class loader
    for (int i=0; i<bi_args.length; i++) {
      bi_args[i] = args[i+1];
      // jdp_console.writeOutput("arg " + i + ": " + bi_args[i]);
    }

    // find the class loader and invoke it with the bootImageWriter
    try {
      pub_cl = Class.forName(args[0]);
      pub_obj = pub_cl.newInstance();
      pub_methods = pub_cl.getMethods();
      // jdp_console.writeOutput("got class loader: " + pub_cl.toString());
      // jdp_console.writeOutput("class object: " + pub_obj.toString());

      for (int n=0; n<pub_methods.length; n++) {
        // jdp_console.writeOutput("found method " + pub_methods[n].toString());
        if (pub_methods[n].getName().equals("main")) {
          // invoke takes an array of arguments to "main"; 
          // main takes one argument, an array of strings.  
          Object invoke_args[] = {bi_args};
          pub_methods[n].invoke(pub_obj, invoke_args);
          return;
        }
      }

    } catch (ClassNotFoundException e) {
      jdp_console.writeOutput("cannot compile, publicizing class loader not found: " + 
                              args[0]);
      System.exit(1);
    } catch (InstantiationException e1) {
      jdp_console.writeOutput("cannot compile, problem instantiating class");
      System.exit(1);
    } catch (IllegalAccessException e2) {
      jdp_console.writeOutput("cannot compile, illegal access to class");
      System.exit(1);
    } catch (InvocationTargetException e3) {
      jdp_console.writeOutput("cannot compile, Invocation Target Exception:");
      jdp_console.writeOutput(e3.getMessage());
      System.exit(1);
    }
      
  }


  /**
   * Print full information for all or a range of stack frame
   * @param command String containing the command
   * @param args    String array of arguments:  From, To frame number
   *                or frame pointer value in hex
   * @return  
   * @see     
   */
  public void doFullFrame(String command, String[] args) {
    int from, to;
    try {
      switch (args.length) {
      case 0:
	jdp_console.writeOutput(user.mem.printJVMstackTraceFull(0, 20));   // default, print first 20 frames
	break;
      case 1: 
	if (args[0].startsWith("0x") || args[0].startsWith("0X") ||
	    args[0].length()==8) {                // treat number as address for FP
	  int fp = parseHex32(args[0]);
	  jdp_console.writeOutput(user.mem.printJVMstackTraceFull(fp));    
	} else {
	  from = Integer.parseInt(args[0]);       // treat number as starting frame number
	  jdp_console.writeOutput(user.mem.printJVMstackTraceFull(from,from));
	}
	break;
      case 2:
	from = Integer.parseInt(args[0]);         // given from, to frame numbers
	to = Integer.parseInt(args[1]);
	jdp_console.writeOutput(user.mem.printJVMstackTraceFull(from,to));
	break;
      default:
	printHelp(command);
      }	     
    } catch (NumberFormatException e) {
      jdp_console.writeOutput("bad stack frame numbers (decimal) or frame pointer value (hex)");
    }

  }

  /**
   * Print one line for each stack frame, for all or a range of frames
   * @param command String containing the command
   * @param args    String array of arguments:  From, To frame number
   * @return  
   * @see     
   */
  public void doShortFrame(String command, String[] args) {
    int from, to;
    try {
      switch (args.length) {
      case 0:
        jdp_console.writeOutput(user.mem.printJVMstackTrace(0, 20));       // default, print first 20 frames
        break;
      case 1:
	if (args[0].startsWith("0x") || args[0].startsWith("0X") ||
	     args[0].length()==8) {                // treat number as address for FP
	  int fp = parseHex32(args[0]);
	  jdp_console.writeOutput(user.mem.printJVMstackTrace(fp));
	} else {
	  from = Integer.parseInt(args[0]);       // treat number as starting frame number
	  jdp_console.writeOutput(user.mem.printJVMstackTrace(from,from));
	}
        break;
      case 2:
        from = Integer.parseInt(args[0]);         // given from, to frame numbers
        to = Integer.parseInt(args[1]);
        jdp_console.writeOutput(user.mem.printJVMstackTrace(from,to));
        break;
      default:
	printHelp(command);
      }
    } catch (NumberFormatException e) {
      jdp_console.writeOutput("bad stack frame numbers (decimal) or frame pointer value (hex)");
    }
  }

  /**
   * Print the current frame as a dump of memory contents with some annotations
   * @param command String containing the command
   * @param args    String array of arguments: number of memory word to display
   *                and possible frame pointer
   * @return  
   * @see     
   */
  public void doCurrentFrame(String command, String[] args) {
    try {
      int width, fp;
      switch (args.length) {
      case 0:
	jdp_console.writeOutput(user.mem.printJVMstack(0, 4));
	break;
      case 1:
	if (args[0].startsWith("0x") || args[0].startsWith("0X") ||
	     args[0].length()==8) {                // treat number as address for FP
	  fp = parseHex32(args[0]); 
	  jdp_console.writeOutput(user.mem.printJVMstack(fp, 4));
	} else {
	  width = Integer.parseInt(args[0]); 
	  jdp_console.writeOutput(user.mem.printJVMstack(0, width));
	}
	break;
      case 2:
	fp  = parseHex32(args[0]); 
	width = Integer.parseInt(args[1]); 
	jdp_console.writeOutput(user.mem.printJVMstack(fp, width));
	break;
      }
    } catch (NumberFormatException e) {
      printHelp(command);
    }
  }

  /**
   * Clear one or all breakpoints
   * @param command String containing the command
   * @param args    String array of arguments: a hex address, if none clear all
   * @return  
   * @see     
   */
  public void doClearBreakpoint(String command, String[] args) {
    if (args.length==0) {
      user.bpset.clearBreakpoint();                   // clear current breakpoint
      jdp_console.writeOutput("breakpoint cleared");
    } else if (args[0].equals("all")) {
      user.bpset.clearAllBreakpoint();                // clear all breakpoints
      jdp_console.writeOutput("all breakpoints cleared");      
    } else {
      try {
	int addr = parseHex32(args[0]);   // clear specific breakpoint
	breakpoint bp = user.bpset.lookup(addr);
	if (bp!=null)
        {
	  user.bpset.clearBreakpoint(bp);
          jdp_console.writeOutput("breakpoint cleared");
        }
	else
	  jdp_console.writeOutput("no breakpoint at " + args[0]);
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("Clear breakpoint: please specify hex address");
      }
    }
  }

  /**
   * Set a breakpoint by hex address or method name with line number
   * @param command String containing the command
   * @param args    String array of arguments: hex address or method name with line number
   *                signature
   * @return  
   * @see     
   */
  public boolean doSetBreakpoint(String command, String[] args) {

//      System.err.println("doSetBreakpoint >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
//      Thread.dumpStack();
//      System.err.println("doSetBreakpoint <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

    breakpoint bp = null;

    if (args.length!=0) {
      // first try to compute the breakpoint
      try {

	// Case 1:  breakpoint given as hex address, find the method ID
	int addr = parseHex32(args[0]);        
	// *******************************
	// Compute the method ID and offset for this raw address 
	// to make the breakpoint relocatable
	// int compiledMethodID = user.bmap.getCompiledMethodID(user.reg.currentFP(), addr);
	// if (compiledMethodID==0) {
	//   jdp_console.writeOutput("There is no code at address " + VM.intAsHexString(addr));
	//   return;
	// } else if (compiledMethodID==NATIVE_METHOD_ID) {
	//   
	// }
	// int offset = addr - user.bmap.instructionAddress(compiledMethodID);
	// *******************************
	// Don't compute the method ID, just set a raw address breakpoint (nonrelocatable)
	jdp_console.writeOutput("Caution: setting breakpoint at raw address. \n" + 
				"  If the code is moved by GC, the breakpoint will be lost \n" + 
				"  and the trap instruction will be left in the code.");
	bp = new breakpoint(addr);
      } catch (NumberFormatException e) {

	// Case 2:  breakpoint given as symbolic name, try looking it up
	String sig=null;
	if (args.length>=2) {                    // if signature also given
	  sig = args[1];
	} 
	try { bp = user.bmap.findBreakpoint(args[0], sig, user.reg.hardwareIP()); } 
	catch (BmapMultipleException e1) { jdp_console.writeOutput(e1.getMessage()); } 
	catch (BmapNotFoundException e2) { jdp_console.writeOutput(e2.getMessage()); }
      }

      // then try to set the breakpoint
      if (bp != null) {
	user.bpset.setBreakpoint(bp);
	jdp_console.writeOutput("breakpoint at: " + bp.toString(user.bmap));
      } 

    } else {
      // just print the list
      jdp_console.writeOutput(user.bpset.list());
      // Only the Intel version prints anything with this call
      Platform.printbp();
    }

    return bp != null;
  }

  /**
   * Disassemble the machine instruction.  Branch target address is not
   * converted to symbolic name because the link register is not available.
   * @param command String containing the command
   * @param args    String array of arguments: hex address and count
   * @return  
   * @see     
   */
  public void doListInstruction(String command, String[] args) {
    int count;
    int addr = -1;
    try {
      switch (args.length) {
      case 0:
	addr = user.reg.currentIP();
	jdp_console.writeOutput(user.mem.listInstruction(addr, 10));
	// for (int i=0; i<10; i++) {
	//   int instruction = user.mem.read(addr);
	//   jdp_console.writeOutput(VM.intAsHexString(addr) + " : " + VM.intAsHexString(instruction) + "\t" +
	// 			PPC_Disassembler.disasm(instruction, 0));
	//   addr+=4;
	// }
	break;
      case 1:
	if (args[0].startsWith("0x") || args[0].startsWith("0X")) {
	  addr = parseHex32(args[0]);
	  count = 10;
	}
	else {
	  addr = user.reg.currentIP();
	  count = Integer.parseInt(args[0]);
	  if (count < 0 && Platform.listiNegCountImplemented == 0) {
	    jdp_console.writeOutput("Sorry, use of a negative count is not available on this platform");
	    break;
	  }
	}
	jdp_console.writeOutput(user.mem.listInstruction(addr, count));
	break;
      default:
	addr = parseHex32(args[0]);
	count = Integer.parseInt(args[1]);
	if (count < 0 && Platform.listiNegCountImplemented == 0) {
	  jdp_console.writeOutput("Sorry, use of a negative count is not available on this platform");
	  break;
	}
	jdp_console.writeOutput(user.mem.listInstruction(addr, count));
	break;
      }
    } catch (NumberFormatException e) {
      jdp_console.writeOutput("If an address is specified it must be as a hex number, any count specified must be an integer");
    }
  }

  /**
   * Write to memory location
   * @param command String containing the command
   * @param args    String array of arguments: hex address and value
   * @return  
   * @see     
   */
  public void doMemoryWrite(String command, String[] args) {
    if (args.length==2) {
      try {
	int data = parseHex32(args[1]);
	int addr = parseHex32(args[0]);
	user.mem.write(addr,data);
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad value for write: " + args[0] + ", " + args[1]);
      }
    } else {
      printHelp(command);
    }
  }

  /**
   * Read memory location, the breakpoints are transparent
   * @param command String containing the command
   * @param args    String array of arguments:  hex address and count
   * @return  
   * @see     
   */
  public void doMemoryRead(String command, String[] args) {
    int addr, count;
    switch (args.length) {
    case 0:       // no address specified, skip
      printHelp(command);
      break;
    case 1:       // no count specified
      try {
	addr = parseHex32(args[0]);
	jdp_console.writeOutput(user.mem.print(addr, 5));
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad address: " + args[0]);
      }
      break;
    default:      // print the specified number of locations
      try {
	addr = parseHex32(args[0]);
	count = Integer.parseInt(args[1]);
	jdp_console.writeOutput(user.mem.print(addr, count));
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad address or count: " + args[0] + ", " + args[1]);
      }	  
      break;
    }
  }

  /**
   * Read memory location without making the breakpoints transparent
   * @param command String containing the command
   * @param args    String array of arguments:  hex address and count
   * @return  
   * @see     
   */
  public void doMemoryReadRaw(String command, String[] args) {
    StringBuffer ret = new StringBuffer();
    ret.append("Actual memory (breakpoints shown as is):\n");
    int addr, count;
    switch (args.length) {
    case 0:       // no address specified, skip
      jdp_console.writeOutput(ret.toString());
      printHelp(command);
      break;
    case 1:       // no count specified
      try {
	addr = parseHex32(args[0]);
	ret.append(user.mem.printRaw(addr, 5));
      } catch (NumberFormatException e) {
	ret.append("bad address: " + args[0] + "\n");
      }
      jdp_console.writeOutput(ret.toString());      
      break;
    default:      // print the specified number of locations
      try {
	addr = parseHex32(args[0]);
	count = Integer.parseInt(args[1]);
	ret.append(user.mem.printRaw(addr, count));
      } catch (NumberFormatException e) {
	ret.append("bad address or count: " + args[0] + ", " + args[1] + "\n");
      }
      jdp_console.writeOutput(ret.toString());      
      break;
    }
  
  }

  /**
   * Write into register
   * @param command String containing the command
   * @param args    String array of arguments:  register name or number, value
   * @return  
   * @see     
   */
  public void doRegisterWrite(String command, String[] args) {
    if (args.length==2) {
      try {
	int regnum = Integer.parseInt(args[0]);
	int data = parseHex32(args[1]);
	user.reg.write(regnum, data);
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad value for write: " + args[0] + ", " + args[1]);
      }
    } else {
      printHelp(command);
    }
  }

  /**
   * Print the value of registers
   * If no register specified, print all.
   * @param command String containing the command
   * @param args    Array of string: register number/name and count
   * @return  
   * @see     
   */
  public void doRegisterRead(String command, String[] args) {
    try {
      switch (args.length) {
      case 0:       // no register specified, print all
        jdp_console.writeOutput(user.reg.getValue("0", 0));
	break;
      case 1:       // no count specified, print just this register
        jdp_console.writeOutput(user.reg.getValue(args[0], 1));
	break;
      case 2:
	try {
	  int count = Integer.parseInt(args[1]);	
	  jdp_console.writeOutput(user.reg.getValue(args[0], count));
	} catch (NumberFormatException e) {
	  jdp_console.writeOutput("bad count: " + args[1]);
	}	  
	break;
      default:  
	printHelp(command);
      }
    } catch (Exception e) {
      jdp_console.writeOutput(e.getMessage());
    }

  }

  /**
   * Print the symbolic names of the registers
   * @param command String containing the command
   * @return  
   * @see     
   */
  public void doRegisterName(String command) {
    try {
      jdp_console.writeOutput(user.reg.getNames());
    } catch (Exception e) {
      jdp_console.writeOutput(e.getMessage());
    }
  }

  /**
   * Print the value of static fields of a class
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)
   * @return  
   * @see     
   */
  public void doPrintClassCommand(String command, String[] args) {
    if (args.length==0) 
      return;

    try {
      int addr = parseHex32(args[0]);
      jdp_console.writeOutput(user.bmap.addressToClassString(addr));
    } catch (NumberFormatException e) {
      try {
	jdp_console.writeOutput(args[0] + " = " + user.bmap.staticToString(args[0]));
      } catch (BmapNotFoundException e1) {
	jdp_console.writeOutput(e1.getMessage());
      }
    } catch (memoryException e1) {
      jdp_console.writeOutput(args[0] + " is not a valid object address");
    }

  }

  /**
   * send a JDP_Class object representing the static fields   * of a class to the network client
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)
   */
  public void doGetClassCommand(String command, String[] args)
  {
    try
    {
      JDP_Class jdpClass = user.bmap.objectToJDPClass(args[0], 0, true);
      jdpClass.fields.trimToSize();
      jdp_console.writeOutput(jdpClass);
    }
    catch (BmapNotFoundException e1)
    {
      jdp_console.writeOutput(e1.getMessage());
    }
    catch (memoryException e2)
    {
      jdp_console.writeOutput(args[0] + " is not a valid object address");
    }
    catch (NoSuchClassException e3)
    {
      jdp_console.writeOutput(args[0] + " is an invalid class name");
    }
  }

  /**
   * send a JDP_Class object representing the fields of
   * an object instance to the network client
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)
   */   
  public void doGetInstanceCommand(String command, String[] args)
  {
    if (args[0].startsWith("("))
    {
      int rparen = args[0].indexOf(')');
      if (rparen==-1) {
	jdp_console.writeOutput("missing parenthesis for class name: " + args[0]);
        return;
      }
      try {
        JDP_Class jdpClass = new JDP_Class();
	jdpClass.address = parseHex32(args[1]);
	jdpClass.name = args[0].substring(1,rparen);
        jdpClass.instance = true;
	try {
          user.bmap.classToJDPClass(jdpClass.name,
                                    jdpClass.address,
                                    false, jdpClass); // flag set to false to get only nonstatic fields
          jdpClass.fields.trimToSize();
	  jdp_console.writeOutput(jdpClass);
	} catch (memoryException e) {
	  jdp_console.writeOutput( "(" + e.getMessage() + ")");
	} catch (NoSuchClassException e2) {
          jdp_console.writeOutput(jdpClass.name + " is an invalid class name");
        }
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad address for casting: " + args[1]);
      }
    }
    else
    {
      jdp_console.writeOutput("invalid args for getinstance");
    }
    return;
  }
  /**
   * sends a JDP_Class object representing the elements
   * of an array to the network client
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)
   */     
  public void doGetArrayCommand(String command, String[] args)
  {
    // args[0] is className.fieldName
    // args[1] is address
    try {
      JDP_Class jdpClass = user.bmap.arrayTypeToJDPClass(args[0],
                                                         args[1],
                                                         parseHex32(args[2]),
                                                         false);
      // flag set to false to get only nonstatic fields
      jdpClass.fields.trimToSize();
      jdp_console.writeOutput(jdpClass);
    } catch (memoryException e) {
      jdp_console.writeOutput( "(" + e.getMessage() + ")");
    } catch (NoSuchClassException e2) {
      jdp_console.writeOutput(args[0] + " is an invalid class name");
    } catch (NumberFormatException e3) {
      jdp_console.writeOutput("bad address for casting: " + args[1]);
      e3.printStackTrace();
    } catch (BmapNotFoundException e4) {
      jdp_console.writeOutput(args[0] + " is an invalid class name");
    }
    
    return;
  }

  /**
   * print the current class name and line number
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)
   */     
  public void doGetClassAndLine(String command, String[] args)
  {
    jdp_console.writeOutput(user.bmap.getCurrentClassAndLineNumber());
  }

  /**
   * print the current instruction address
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)   
   */
  public void doGetCurrentInstrAddr(String command, String[] args)
  {
    jdp_console.writeOutput(String.valueOf(user.reg.currentIP()));
  }

  /**
   * send the client a Vector of JDP_Frames representing
   * the current frames on the stack
   * @param command String containing this command
   * @param args  Array of string arguments (symbolic name)   
   */
  public void doGetFrames(String command, String[] args)
  {
    jdp_console.writeOutput(user.bmap.getFrames());
  }

  /**
   * send the client a JDP_Locals object representing
   * the locals in the requested frame
   * @see BootMap.localsToJDPLocals
   */
  public void doGetLocals(String command, String[] args)
  {
    try
    {
      jdp_console.writeOutput(user.bmap.localsToJDPLocals(Integer.parseInt(args[0]),
                                                          Integer.parseInt(args[1]),
                                                          Integer.parseInt(args[2]),
                                                          Integer.parseInt(args[3])));
    }
    catch (NumberFormatException e)
    {
      jdp_console.writeOutput("bag argument format");
    }
  }
    
  
  /**
   * Print the value of local variables
   * @param command String containing this command
   * @param args  Array of string arguments: symbolic name
   * @return  
   * @see     
   */
  public void doPrintCommand(String command, String[] args) {
    int addr, frame;
    String varname;

    // no args, print this class and local variable for top stack frame (0)
    if (args.length==0) {
      jdp_console.writeOutput(user.bmap.localVariableToString(0, null));
      return;
    }

    // Want the address 
    // if (args[0].startsWith("@")) {
    // 	 String name = args[0].substring(1);
    // 	 try {
    // 	   addr = user.bmap.findFieldAsAddress(name);
    // 	   jdp_console.writeOutput(name + " at " + Integer.toHexString(addr));
    // 	 } catch (BmapNotFoundException e1) {
    // 	   jdp_console.writeOutput(e1.getMessage());
    // 	 }
    // 	 return;
    // } 
    
    String arg1;
    // want to cast a class on an address
    if (args[0].startsWith("(")) {
      int rparen = args[0].indexOf(')');
      if (rparen==-1) {
	jdp_console.writeOutput("missing parenthesis for class name: " + args[0]);
        return;
      }
      if (args.length == 1) {
	if (rparen != (args[0].length()-1)) {
	  // User must have not put a blank
	  arg1 = args[0].substring(rparen+1);
	} else {
	  jdp_console.writeOutput("Please specify an address to be cast e.g. print (VM_Thread) 0x4169536c");
	  return;
	}
      } else
	arg1 = args[1];
	
      try {
	addr = parseHex32(arg1);
	String classname = args[0].substring(1,rparen);
	try {
	  // flag set to false to get only nonstatic fields
	  jdp_console.writeOutput(classname + " = " +
                                  user.bmap.classToString(classname, addr, false));
	} catch (memoryException e) {
	  jdp_console.writeOutput( "(" + e.getMessage() + ")");
	}
      } catch (NumberFormatException e) {
	jdp_console.writeOutput("bad address for casting: " + args[1]);
      }
      return;
    } 

    // Want the value:  
    // if args is a string, it's a local variable in frame 0 (default)
    // if args is a number, it's a stack frame number
    // if args is n:name, it's a local variable in stack frame n
    // if args is "this" or "n:this", it's for the current class of stack frame n
    frame = CommandLine.localParseFrame(args[0]);
    varname = CommandLine.localParseName(args[0]);

    // for the case when a stack frame is specified
    if (frame!=-1) {
      if (varname==null) {
	jdp_console.writeOutput(user.bmap.localVariableToString(frame, null));
      } else {
	if (varname.equals("this") || varname.startsWith("this."))
	  jdp_console.writeOutput(args[0] + " = " + user.bmap.currentClassToString(frame, varname));
	else	  
	  jdp_console.writeOutput(user.bmap.localVariableToString(frame, varname));
      }
      return;
    } 

    // for the case when no frame number is specified: default to frame 0
    if (varname.equals("this") || varname.startsWith("this."))
      jdp_console.writeOutput(args[0] + " = " + user.bmap.currentClassToString(0, varname));
    else	  
      jdp_console.writeOutput(user.bmap.localVariableToString(0, varname));	  
    return;

  }

  /** 
   * Set thread context
   * @param command String containing this command
   * @param args  Array of string arguments: thread id
   */
  public void doThread(String command, String[] args) {    
    int threadID, threadPointer;
    try {
      switch (args.length) {
      case 0:
	// See if context had been set; report what it had been set to
	threadID = user.reg.getContextThreadID();
	if (threadID != 0)
	  jdp_console.writeOutput("context had been set to thread: " + threadID);
	// return to the thread context in the hardware (R15)
	threadID = user.reg.registerToTPIndex(user.reg.hardwareTP());
	jdp_console.writeOutput("setting context to executing thread: " + threadID);
	user.reg.setContextThreadID(threadID);
	break;
      case 1:
	if (args[0].equalsIgnoreCase("off")) {
	  // set context to 0 to force using the hardware register
	  user.reg.setContextThreadID(0);
	} else {
	  // set the thread context for future display
	  threadID = Integer.parseInt(args[0]);
	  user.reg.setContextThreadID(threadID);	
	}
	break;
      default:
	printHelp(command);
      }
    } catch (NumberFormatException e) {
      jdp_console.writeOutput("invalid thread ID");
    } catch (Exception e1) {
      jdp_console.writeOutput(e1.getMessage());
    }
  }

  /** 
   * List threads
   * @param command String containing this command
   * @param args  
   */
  public void doListThread(String command, String[] args) {
    if (args.length==0) {      
      // print the list of all current threads
      jdp_console.writeOutput(user.listAllThreads(true));

    } else if (args.length==1) {
      // print the list of selective threads
      if (args[0].equals("all")) {
	// all threads
	jdp_console.writeOutput(user.listAllThreads(false));

      } else if (args[0].equals("byname")) {
	// threads by their class name
	jdp_console.writeOutput(user.listAllThreads(true));

      } else if (args[0].equals("ready")) {
	// threads in the ready queue
	jdp_console.writeOutput(user.listReadyThreads());

      } else if (args[0].equals("wakeup")) {
	// threads in the wake up queue
	jdp_console.writeOutput(user.listWakeupThreads());

      } else if (args[0].equals("run")) {
	// threads loaded in the system threads
	if (Platform.listtRunImplemented == 1) {
	  jdp_console.writeOutput(user.listRunThreads());
	} else {
	  jdp_console.writeOutput("Sorry, listt run is not implemented yet on this platform");
	}
      } else if (args[0].equals("system")) {
	// dump the system threads
	if (Platform.listtSystemImplemented == 1) {
	  jdp_console.writeOutput(user.listSystemThreads());
	} else {
	  jdp_console.writeOutput("Sorry, listt system is not implemented yet on this platform");
	}
      } else if (args[0].equals("gc")) {
	// GC thread
	jdp_console.writeOutput(user.listGCThreads());

      } else {
	printHelp(command);
      }
    } else {
      printHelp(command);
    }
  }

  /** 
   * Set preference for jdp
   * @param command String containing this command
   * @param args  Array of string arguments: preferences
   */
  public void doSetPreference(String command, String[] args) {
    StringBuffer ret = new StringBuffer();
    if (args.length==0) {
      // print the current preferences
      ret.append("Current preferences: \n");
      ret.append("  integer = " + integerPreference + "\n");
      ret.append("  stack = "   + stackPreference + "\n");
      ret.append("  fpr = "     + fprPreference + "\n");
      ret.append("  showFPRs = ");
      if (showFPRsPreference) 
	ret.append(" true\n");
      else
	ret.append(" false\n");
      jdp_console.writeOutput(ret.toString());

    } else if (args[0].equals("int")) {
      if (args[1].equals("hexadecimal") || args[1].equals("hex") || args[1].equals("x"))
	integerPreference = 'x';
      else if (args[1].equals("decimal") || args[1].equals("dec") || args[1].equals("d"))
	integerPreference = 'd';
      else 
	printHelp(command);
      return;
    } else if (args[0].equals("stack")) {
      if (args[1].equals("hexadecimal") || args[1].equals("hex") || args[1].equals("x"))
	stackPreference = 'x';
      else if (args[1].equals("decimal") || args[1].equals("dec") || args[1].equals("d"))
	stackPreference = 'd';
      else 
	printHelp(command);
      return;
    } else if (args[0].equals("fpr")) {
      if (args[1].equals("hexadecimal") || args[1].equals("hex") || args[1].equals("x"))
	fprPreference = 'x';
      else if (args[1].equals("float") || args[1].equals("f"))
	fprPreference = 'f';
      else 
	printHelp(command);
      return;
    } else if (args[0].equals("showFPRs") || args[0].equals("showfprs")) {
      if (args[1].equals("true"))
	showFPRsPreference = true;
      else if (args[1].equals("false")) 
	showFPRsPreference = false;
      else {
	jdp_console.writeOutput("Sorry, value should be 'true' or 'false'\n");
	printHelp(command);
      }
    } else {
      jdp_console.writeOutput("Sorry, I do not recognize your preference request");
      printHelp(command);
    }
  }

  private void doConvertHexToInt(String command, String[] args) {
      if (args.length!=0) {
        try {
          int num = parseHex32(args[0]);
          jdp_console.writeOutput(args[0] + " = " + num);
        } catch (NumberFormatException e) {
          jdp_console.writeOutput("cannot convert, bad number: " + args[0]);
        }
      } else {
        System.out.println("Convert hex to decimal, usage:  x2d hexnum");
      }

  }

  private void doConvertIntToHex(String command, String[] args) {
      if (args.length!=0) {
        try {
          int num = Integer.parseInt(args[0]);
          jdp_console.writeOutput(args[0] + " = " + Integer.toHexString(num));
        } catch (NumberFormatException e) {
          jdp_console.writeOutput("cannot convert, bad number: " + args[0]);
        }
      } else {
        System.out.println("Convert number to hex, usage:  d2x decimalnumber");
      }
  }


  /**
   * Complement Integer.parseInt(hexString, radix) which cannot 
   * handle a 1 as the first bit in a 32 bits hex string (probably
   * considered negative)
   * @param hexString a hex value string
   * @return the integer value
   */
  private int parseHex32(String hexString) throws NumberFormatException {
    if (hexString.startsWith("0x") || hexString.startsWith("0X")) 
      hexString = hexString.substring(2);
    else {
      int tempint = Integer.parseInt(hexString, 16);
      // that will throw an exception if it is a non-hexnum like string
      jdp_console.writeOutput("Sorry, hex numbers must now be preceeded by 0X or 0x. In the future, numbers without this prefix will be interpreted as decimal.");
      throw new NumberFormatException();
    }
    
    int firstInt = Integer.parseInt(hexString.substring(0,1), 16);
    if (hexString.length() < 8  || firstInt <= 7)
      return Integer.parseInt(hexString,16);
    else if (hexString.length() == 8) {
      int lower = Integer.parseInt(hexString.substring(1,hexString.length()), 16);      
      return lower + (firstInt << 28);
    } else
      throw new NumberFormatException();
  }

  /**
   * Print usage information for a particular command.
   * This is just a convenient way to delegate to the HelpCommand object.
   * @param commandName name of the command to print help information for
   */
  void printHelp(String commandName) {
    commandTable[HELP_COMMAND].execute(new String[]{commandName});
  }

  // Misc stuff by palm
  final static boolean debug = false; //true;
  private final void debug(Object msg) { if (debug) System.err.println(msg); }
  final static String args(String[] args) {
    if (args == null) return "<null>";
    StringBuffer sb = new StringBuffer("[");
    for (int i = 0, N = args.length; i < N; i++) {
      sb.append(args[i]);
      if (i <N-1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

}
