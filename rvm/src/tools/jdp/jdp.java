/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * jdp main program:
 *   parse the command line to pick up jdp arguments and collect the user program arguments,
 *   then run the debugger thread
 * @author Ton Ngo  1/15/98
 */
import java.io.*;

class jdp {

  public jdp() {
    
  }

  public static void main(String args[])  {
    String  userArgs[] = null;  // args for user program
    int initial_bp = 0;
    String bi_runner = null;   // string for loading and running the boot image 
    String initial_macro = null;
    boolean rawMode = false;
    boolean attach = false;
    boolean interpreted = true;
    boolean commandLine = true;
    boolean viewBoot = false; // if false, will start debugger at main() of user program
    boolean dejavu = false; // true if we're recording or replaying
    int processID = 0;
    int cnt, i;

    // parse the debugger arguments and separate those for the user program
    // Note that in the java convention, args[] does not include
    // the main program name.
    // So args[0] is not "jdp", it is really the first argument of jdp.
    for (i = 0; i < args.length; i++)  {
      
      // System.out.println("parsing " + i + ": " + args[i]);
      String arg = args[i];    
      // pick up the initial breakpoint
      if (arg.equals("-jdpbreakpoint")) {
	if (i == args.length - 1) {
	  System.out.println("jdp: missing breakpoint value for -b");
	  System.exit(1);
	}
	initial_bp = Integer.parseInt(args[++i],16);
	continue;
      }
      
      // pick up the C program that will load the boot image
      else if (arg.equals("-jdpbootrunner")) {
	if (i == args.length - 1) {
	  System.out.println("jdp: missing name for bootimage loader");
	  System.exit(1);
	}
	bi_runner = args[++i];
      }   

      // pick up the process ID if attaching to a current process
      else if (arg.startsWith("-jdpattach")) {
	attach = true;
	processID = Integer.parseInt(arg.substring(10));
      }

      // NOTE: this mode is needed for command line retrieval, no longer supported
      // Pick up the flag to indicate whether the terminal is in 
      // raw or cooked TTY mode:  -c means it's cooked
      // else if (arg.equals("-c")) {
      //   rawMode = false;	
      // }

      // NOTE: use startup.jdp file instead
      // pick up any macro file
      // else if (arg.equals("-jdpmacro")) {
      //   if (i == args.length - 1) {
      //     System.out.println("jdp: missing file name for macro file");
      //     System.exit(1);
      //   }
      //   initial_macro = args[++i];
      // }   

      // pick up the flag indicating whether we are running under the interpreter
      else if (arg.equals("-jdpbootonly")) {
	interpreted = false;
      }


      // help message
      else if (arg.equals("-h") || arg.equals("-help")) {
	printHelpMessage();
	System.out.println("Exiting Java.");
	System.exit(1);
      }

      // flag for running in server mode
      else if (arg.startsWith("-jdpserver")) {
        commandLine = false;
      }

      else if (arg.startsWith("-jdpviewboot")) {
        viewBoot = true;
      }

      else if (arg.startsWith("-jdprecord") || arg.startsWith("-jdpreplay"))
      {
        dejavu = true;
      }
            
      // no more valid jdp args, drop out
      else {
	break;
      }
    }

    // pick up the rest of the arguments to make up the user program args
    cnt = args.length - i;
    if (cnt != 0) {
      userArgs = new String[cnt];
      for (int j = 0; j < cnt; j++) {
	if (args[i].equals("-h") || args[i].equals("-help")) {
	  printHelpMessage();
	  System.exit(1);
	}
	userArgs[j] = args[i++];
	// System.out.println(j + " " + i + " "+ userArgs[j]);
      }
    } else {
      System.out.println("jdp: no program specified");
      System.exit(1);
    }

    // Some quick checks before launching to see if we have everything
    if (bi_runner==null) {
      System.out.println("Please specify a boot runner: -r boot_runner ");
      System.exit(1);      
    } 

    // Create the debugger: either create the user process or attach to a current one
    if (commandLine)
    {
      // run in command line mode
      CommandLineDebugger db;
      if (attach) {
        db = new CommandLineDebugger(0, null, false, interpreted, null, viewBoot, dejavu);
        db.runAttached(processID, userArgs);
      } else {
        db = new CommandLineDebugger(initial_bp, bi_runner, rawMode, interpreted, initial_macro, viewBoot, dejavu);
        if (args.length==0) {
          System.out.println("no program specified");
          System.exit(1);
        } else {
          db.run(userArgs);
        }
      }
    }
    else
    {
      // run in server mode
      NetworkDebugger db;
      if (attach) {
        db = new NetworkDebugger(0, null, false, interpreted, null, viewBoot, dejavu);
        db.runAttached(processID, userArgs);
      } else {
        db = new NetworkDebugger(initial_bp, bi_runner, rawMode, interpreted, initial_macro, viewBoot, dejavu);
        if (args.length==0) {
          System.out.println("no program specified");
          System.exit(1);
        } else {
          db.run(userArgs);
        }
      }
    }
  }

  /**
   * Only show the user-visible options
   */
  private static void printHelpMessage() {
    System.out.println("Usage: jdp <optional args>  yourClassName <yourArgs>");
    System.out.println("Optional arguments:  ");
    System.out.println("    < -jdpattach<processID> > to attach debugger to a currently running JVM");
    System.out.println("    < -jdpbootonly> to debug boot image only, no dynamically loaded classes");

    System.exit(0);
  }

}
