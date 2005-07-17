/*
 * (C) Copyright 
 * Department of Computer Science,
 * University of Texas at Austin 2005
 * All rights reserved.
 */

// $Id$

package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.classloader.VM_NormalMethod;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_RuntimeCompiler;
import com.ibm.JikesRVM.opt.*;
import java.io.*;

/**
 * This class provides advice file used by compile replay experiments
 * Right now this class is basically duplicate part of the VM_AOSLogging
 * class.
 *
 * @author Xianglong Huang
 */
public class VM_AOSGenerator {

  /*
   * The output file stream, where all log messages will go
   */
  private static PrintStream log;
  
  /*
   * Record that the AOS logging has been booted.
   * Needed to allow fast exit from reporting to ensure
   * that when no class is specified to be run but "-help" is specified, 
   * don't want null pointer exception to occur!
   */
  private static boolean booted = false;

  // variable used to avoid recursive calls
  private static boolean recording = false;
  /**
   * Return whether AOS logging has booted.
   * @return whether AOS logging has booted
   */
  public static boolean booted() {
    return booted;
  }
  
  /**
   * Called from VM_ControllerThread.run to initialize the logging subsystem
   */
  public static void boot() {
    VM.sysWrite("AOS generation booted\n");
    try {
      log = new PrintStream(new FileOutputStream(VM_Controller.options.COMPILATION_ADVICE_FILE_OUTPUT));      
    }
    catch (IOException e) {
      VM.sysWrite("IOException caught in VM_AOSGenerator.java while trying to create and start log file.\n");
      VM.sysWrite("Please check for file permission problems\n");
    }
    booted = true; recording = false;
  }

  ////////////////////////////////////////////////////////////////
  // Logging level 2
  ////////////////////////////////////////////////////////////////

  /**
   * This method logs the successful completion of an adaptively 
   * selected recompilation
   * @param plan the OPT_Compilation plan being executed.
   */
  public static void reCompilationWithOpt(OPT_CompilationPlan plan) {
    if (!booted) return;
    synchronized (log) {
      log.println(plan.method.getDeclaringClass().getDescriptor()+ " "+
                  plan.method.getName()+" "+
                  plan.method.getDescriptor()+
                  " 3 "+ /*it's always OPT_compiler*/
                  plan.options.getOptLevel()
                  );
    }
  }

  public static void baseCompilationCompleted(VM_CompiledMethod cm) {
    if (recording || (!booted)) return;
    synchronized (log) {
      recording = true;
      log.println(cm.getMethod().getDeclaringClass().getDescriptor()+ " "+
                  cm.getMethod().getName()+" "+
                  cm.getMethod().getDescriptor()+" "+
                  cm.getCompilerType()+" "+ /*it's always baseline compiler*/
                  "-1"
                  );
      recording = false;
    }
  }
}
