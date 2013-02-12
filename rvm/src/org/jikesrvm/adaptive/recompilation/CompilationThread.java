/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.adaptive.recompilation;

import org.jikesrvm.adaptive.OnStackReplacementPlan;
import org.jikesrvm.adaptive.controller.Controller;
import org.jikesrvm.adaptive.controller.ControllerPlan;
import org.jikesrvm.scheduler.SystemThread;
import org.vmmagic.pragma.NonMoving;

/**
 *  This class is a separate thread whose job is to monitor a (priority)
 *  queue of compilation plans.  Whenever the queue is nonempty, this
 *  thread will pick the highest priority compilation plan from the queue
 *  and invoke the OPT compiler to perform the plan.
 *  <p>
 *  No intelligence is contained in this class.  All policy decisions are
 *  made by the ControllerThread.
 */
@NonMoving
public final class CompilationThread extends SystemThread {

  /**
   * constructor
   */
  public CompilationThread() {
    super("CompilationThread");
  }

  /**
   * This is the main loop of the compilation thread. Its job is to
   * remove controller plans from the compilation queue and perform
   * them.
   */
  @Override
  public void run() {
    // Make a blocking call to deleteMin to get a plan and then execute it.
    // Repeat...
    while (true) {
      Object plan = Controller.compilationQueue.deleteMin();
      if (plan instanceof ControllerPlan) {
        ((ControllerPlan) plan).doRecompile();
      } else if (plan instanceof OnStackReplacementPlan) {
        ((OnStackReplacementPlan) plan).execute();
      }
    }
  }

}

