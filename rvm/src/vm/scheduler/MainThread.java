/*
 * (C) Copyright IBM Corp 2001,2002,2004
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;

//-#if RVM_WITH_GCSPY
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
//-#endif

/**
 * Thread in which user's "main" program runs.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * @modified Steven Augart
 */
class MainThread extends Thread {
  private String[] args;
  private VM_Method mainMethod;
  protected boolean launched = false;
   
  private final static boolean dbg = false;
  

  /**
   * Create "main" thread.
   * Taken: args[0]    = name of class containing "main" method
   *        args[1..N] = parameters to pass to "main" method
   */
  MainThread(String args[]) {
    super(args); // special constructor to create thread that has no parent
    this.args = args;
    this.vmdata.isMainThread = true;
    //-#if RVM_WITH_OSR
    this.vmdata.isSystemThread = false;
    //-#endif
    if (dbg) VM.sysWriteln("MainThread(args.length == ", args.length,
                         "): constructor done");
    
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
  public void run () {

    if (dbg) VM.sysWriteln("MainThread.run() starting ");
      //-#if RVM_WITH_GCSPY
      // start the GCSpy interpreter server
      MM_Interface.startGCSpyServer();
      //-#endif

    // Set up application class loader
    ClassLoader cl = VM_ClassLoader.getApplicationClassLoader();
    setContextClassLoader(cl); 

    if (dbg) VM.sysWrite("[MainThread.run() loading class to run... ");
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
      if (dbg) VM.sysWrite("failed.]");
      // no such class
      VM.sysWrite(e+"\n");
      return;
    }
    if (dbg) VM.sysWriteln("loaded.]");

    // find "main" method
    //
    mainMethod = cls.findMainMethod();
    if (mainMethod == null) { 
      // no such method
      VM.sysWrite(cls + " doesn't have a \"public static void main(String[])\" method to execute\n");
      return;
    }

    if (dbg) VM.sysWrite("[MainThread.run() making arg list... ");
    // create "main" argument list
    //
    String[] mainArgs = new String[args.length - 1];
    for (int i = 0, n = mainArgs.length; i < n; ++i)
      mainArgs[i] = args[i + 1];
    if (dbg) VM.sysWriteln("made.]");
    
    if (dbg) VM.sysWrite("[MainThread.run() compiling main(String[])... ");
    mainMethod.compile();
    if (dbg) VM.sysWriteln("compiled.]");
    
    // Notify other clients that the startup is complete.
    //
    VM_Callbacks.notifyStartup();

    // dummy call for debugger to find the main method
    // (needed for the default option of stopping in the main method on start up)
    VM.debugBreakpoint();

    launched = true;
    if (dbg) VM.sysWriteln("[MainThread.run() invoking \"main\" method... ");
    // invoke "main" method with argument list
    VM_Magic.invokeMain(mainArgs, mainMethod.getCurrentCompiledMethod().getInstructions());
    if (dbg) VM.sysWriteln("  MainThread.run(): \"main\" method completed.]");
  }
}
