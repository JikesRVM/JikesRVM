/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.adaptive;

import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;
//-#if RVM_WITH_OSR
import com.ibm.JikesRVM.adaptive.OSR_OnStackReplacementPlan;
//-#endif

/**
 *  This class is a separate thread whose job is to monitor a (priority)
 *  queue of compilation plans.  Whenever the queue is nonempty, this
 *  thread will pick the highest priority compilation plan from the queue
 *  and invoke the OPT compiler to perform the plan.
 *
 *  No intelligence is contained in this class.  All policy decisions are
 *  made by the controllerThread.
 *
 *  @author Michael Hind
 *  @author David Grove
 */
class VM_CompilationThread extends VM_Thread {

  public String toString() {
    return "VM_CompilationThread";
  }

  /**
   * constructor
   */
  VM_CompilationThread() {
    makeDaemon(true);
  }

  /**
   * This is the main loop of the compilation thread. It's job is to 
   * remove controller plans from the compilation queue and perform
   * them.
   */
  public void run() {
    if (VM.LogAOSEvents) VM_AOSLogging.compilationThreadStarted();

    // Make a blocking call to deleteMin to get a plan and then execute it. 
    // Repeat...
    while (true) {
      Object plan = VM_Controller.compilationQueue.deleteMin();
      //-#if RVM_WITH_OSR
      if (plan instanceof VM_ControllerPlan) {
        ((VM_ControllerPlan)plan).doRecompile();
      } else if (plan instanceof OSR_OnStackReplacementPlan) {
        ((OSR_OnStackReplacementPlan)plan).execute();
      }
      //-#else
      ((VM_ControllerPlan)plan).doRecompile();
      //-#endif
    }
  }

}
