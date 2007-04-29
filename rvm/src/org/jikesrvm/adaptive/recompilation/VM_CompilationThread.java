/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.adaptive.recompilation;

import org.jikesrvm.adaptive.OSR_OnStackReplacementPlan;
import org.jikesrvm.adaptive.controller.VM_Controller;
import org.jikesrvm.adaptive.controller.VM_ControllerPlan;
import org.jikesrvm.adaptive.util.VM_AOSLogging;
import org.jikesrvm.scheduler.VM_Thread;

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
public class VM_CompilationThread extends VM_Thread {

  public String toString() {
    return "VM_CompilationThread";
  }

  /**
   * constructor
   */
  public VM_CompilationThread() {
    makeDaemon(true);
  }

  /**
   * This is the main loop of the compilation thread. It's job is to 
   * remove controller plans from the compilation queue and perform
   * them.
   */
  public void run() {
    VM_AOSLogging.compilationThreadStarted();

    // Make a blocking call to deleteMin to get a plan and then execute it. 
    // Repeat...
    while (true) {
      Object plan = VM_Controller.compilationQueue.deleteMin();
      if (plan instanceof VM_ControllerPlan) {
        ((VM_ControllerPlan)plan).doRecompile();
      } else if (plan instanceof OSR_OnStackReplacementPlan) {
        ((OSR_OnStackReplacementPlan)plan).execute();
      }
    }
  }

}
