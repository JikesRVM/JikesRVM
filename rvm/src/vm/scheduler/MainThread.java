/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

import java.net.*;

//-#if RVM_WITH_ADAPTIVE_SYSTEM
import com.ibm.JikesRVM.adaptive.VM_Controller;
//-#endif

/**
 * Thread in which user's "main" program runs.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class MainThread extends Thread {
  private String[] args;
  private VM_Method mainMethod;
  protected boolean launched = false;
   
  /**
   * Create "main" thread.
   * Taken: args[0]    = name of class containing "main" method
   *        args[1..N] = parameters to pass to "main" method
   */
  MainThread(String args[]) {
    super(args); // special constructor to create thread that has no parent
    this.args = args;
    //-#if RVM_WITH_OSR
    super.isSystemThread = false;
    //-#endif
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
    // set up JikesRVM socket I/O
    try {
      Socket.setSocketImplFactory(new SocketImplFactory() {
	  public SocketImpl createSocketImpl() { return new JikesRVMSocketImpl(); }
	});
      ServerSocket.setSocketFactory(new SocketImplFactory() {
	  public SocketImpl createSocketImpl() { return new JikesRVMSocketImpl(); }
	});
      DatagramSocket.setDatagramSocketImplFactory(new DatagramSocketImplFactory() {
	  public DatagramSocketImpl createDatagramSocketImpl() { 
	    throw new VM_UnimplementedError ("Need to implement JikesRVMDatagramSocketImpl");
	  }});
    } catch (java.io.IOException e) {
      VM.sysWrite("trouble setting socket impl factories");
    }

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // initialize the controller and runtime measurement subsystems
    VM_Controller.boot();
    //-#endif
    
    // Set up application class loader
    ClassLoader cl = VM_ClassLoader.getApplicationClassLoader();
    setContextClassLoader(cl); 

    // find method to run
    // load class specified by args[0]
    VM_Class cls = null;
    try {
      VM_Atom mainAtom = VM_Atom.findOrCreateUnicodeAtom(args[0].replace('.','/'));
      VM_TypeReference mainClass = VM_TypeReference.findOrCreate(cl, mainAtom.descriptorFromClassName());
      cls = mainClass.resolve().asClass();
      cls.resolve();
      cls.instantiate();
      cls.initialize();
    } catch (NoClassDefFoundError e) { 
      // no such class
      VM.sysWrite(e+"\n");
      return;
    }

    // find "main" method
    //
    mainMethod = cls.findMainMethod();
    if (mainMethod == null) { 
      // no such method
      VM.sysWrite(cls + " doesn't have a \"public static void main(String[])\" method to execute\n");
      return;
    }

    // create "main" argument list
    //
    String[] mainArgs = new String[args.length - 1];
    for (int i = 0, n = mainArgs.length; i < n; ++i)
      mainArgs[i] = args[i + 1];
    
    mainMethod.compile();
    
    // Notify other clients that the startup is complete.
    //
    VM_Callbacks.notifyStartup();

    // dummy call for debugger to find the main method
    // (needed for the default option of stopping in the main method on start up)
    VM.debugBreakpoint();

    launched = true;
    // invoke "main" method with argument list
    VM_Magic.invokeMain(mainArgs, mainMethod.getCurrentCompiledMethod().getInstructions());
  }
}
