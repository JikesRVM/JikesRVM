/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
   * 
   * This code could be made a little shorter by relying on VM_Reflection
   * to do the classloading and compilation.  We intentionally do it here
   * to give us a chance to provide error messages that are specific to
   * not being able to find the main class the user wants to run.
   * This may be a little silly, since it results in code duplication
   * just to provide debug messages in a place where very little is actually
   * likely to go wrong, but there you have it....
   */
  public void run () {

    if (dbg) VM.sysWriteln("MainThread.run() starting ");
    //-#if RVM_WITH_GCSPY
    // start the GCSpy interpreter server
    MM_Interface.startGCspyServer();
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

    launched = true;
    if (dbg) VM.sysWriteln("[MainThread.run() invoking \"main\" method... ");
    // invoke "main" method with argument list
    VM_Reflection.invoke(mainMethod, null, new Object[] {mainArgs}, false);
    if (dbg) VM.sysWriteln("  MainThread.run(): \"main\" method completed.]");
  }
}
