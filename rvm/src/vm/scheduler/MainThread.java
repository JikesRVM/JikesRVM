/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Thread in which user's "main" program runs.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class MainThread extends Thread {
  private String[] args;
  private VM_Method mainMethod;
   
  /**
   * Create "main" thread.
   * Taken: args[0]    = name of class containing "main" method
   *        args[1..N] = parameters to pass to "main" method
   */
  MainThread(String args[]) {
    super(args); // special constructor to create thread that has no parent
    this.args = args;
  }
      
  public String toString() {
    return "MainThread";
  }
      
  VM_Method getMainMethod() {
    return mainMethod;
  }
   
  /**
   * Run "main" thread.
   */
  public void run() {
    // VM_Scheduler.trace("MainThread", "run");
      
    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // initialize the controller and runtime measurement subsystems
    VM_Controller.boot();
    //-#endif

    // Set up application class loader
    VM_ApplicationClassLoader.setPathProperty();
    ClassLoader cl = new VM_ApplicationClassLoader( VM_SystemClassLoader.getVMClassLoader() );

    // find method to run
    String[]      mainArgs = null;
    INSTRUCTION[] mainCode = null;
    synchronized (VM_ClassLoader.lock) {
      // load class specified by args[0]
      //
      VM_Class cls = null;
      try {
	cls = (VM_Class) cl.loadClass(args[0], true).getVMType();
      } catch (ClassNotFoundException e) { 
	// no such class
	VM.sysWrite(e.getException() + "\n");
	return;
      }

      // find "main" method
      //
      mainMethod = cls.findMainMethod();
      if (mainMethod == null) { 
	// no such method
	VM.sysWrite(cls.getName() + " doesn't have a \"public static void main(String[])\" method to execute\n");
	return;
      }

      // create "main" argument list
      //
      mainArgs = new String[args.length - 1];
      for (int i = 0, n = mainArgs.length; i < n; ++i)
	mainArgs[i] = args[i + 1];

      mainCode = mainMethod.compile();
      
    }
   
    // Notify other clients that the startup is complete.
    //
    VM_Callbacks.notifyStartup();

    // dummy call for debugger to find the main method
    // (needed for the default option of stopping in the main method on start up)
    VM.debugBreakpoint();

    // invoke "main" method with argument list
    //
    VM_Magic.invokeMain(mainArgs, mainCode);
  }
}
