/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.Socket;
import java.net.SocketImpl;
import java.net.ServerSocket;
import java.net.SocketImplFactory;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.PrintStream;

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
    // Create file descriptors for stdin, stdout, stderr
    java.io.JikesRVMSupport.setFd(FileDescriptor.in, VM_FileSystem.getStdinFileDescriptor());
    java.io.JikesRVMSupport.setFd(FileDescriptor.out, VM_FileSystem.getStdoutFileDescriptor());
    java.io.JikesRVMSupport.setFd(FileDescriptor.err, VM_FileSystem.getStderrFileDescriptor());

    // Set up System.in, System.out, System.err
    FileInputStream  fdIn  = new FileInputStream(FileDescriptor.in);
    FileOutputStream fdOut = new FileOutputStream(FileDescriptor.out);
    FileOutputStream fdErr = new FileOutputStream(FileDescriptor.err);
    System.setIn(new BufferedInputStream(fdIn));
    System.setOut(new PrintStream(new BufferedOutputStream(fdOut, 128), true));
    System.setErr(new PrintStream(new BufferedOutputStream(fdErr, 128), true));
    VM_Callbacks.addExitMonitor( new VM_Callbacks.ExitMonitor() {
	public void notifyExit(int value) {
	  try {
	    System.err.flush();
	    System.out.flush();
	  } catch (Throwable e) {
	    VM.sysWrite("vm: error flushing stdout, stderr");
	  }
	}
      });

    //-#if RVM_WITH_GNU_CLASSPATH
    // set up JikesRVM socket I/O
    try {
	Socket.setSocketImplFactory(new SocketImplFactory() {
	    public SocketImpl createSocketImpl() { return new JikesRVMSocketImpl(); }
	});
	ServerSocket.setSocketFactory(new SocketImplFactory() {
	    public SocketImpl createSocketImpl() { return new JikesRVMSocketImpl(); }
	});
    } catch (java.io.IOException e) {
	VM.sysWrite("trouble setting socket impl factories");
    }
    //-#endif

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    // initialize the controller and runtime measurement subsystems
    VM_Controller.boot();
    //-#endif
    
    // Set up application class loader
    ClassLoader cl = new ApplicationClassLoader(VM_ClassLoader.getApplicationRepositories());
    setContextClassLoader(cl); 

    // find method to run
    String[]      mainArgs = null;
    // load class specified by args[0]
    //
    VM_Class cls = null;
    try {
      cls = java.lang.JikesRVMSupport.getTypeForClass(cl.loadClass(args[0])).asClass();
      cls.resolve();
      cls.instantiate();
      cls.initialize();
    } catch (ClassNotFoundException e) { 
      // no such class
      VM.sysWrite(e+"\n");
      return;
    } catch (VM_ResolutionException e) { 
      // no such class
      VM.sysWrite(e+"\n");
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
    
    mainMethod.compile();
    
    // Notify other clients that the startup is complete.
    //
    VM_Callbacks.notifyStartup();

    // dummy call for debugger to find the main method
    // (needed for the default option of stopping in the main method on start up)
    VM.debugBreakpoint();

    // invoke "main" method with argument list
    VM_Magic.invokeMain(mainArgs, mainMethod.getCurrentCompiledMethod().getInstructions());
  }
}
