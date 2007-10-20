/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.VM_Callbacks;
import org.mmtk.utility.Constants;

/**
 * This class allows JMTk to register call backs with VM_Callbacks.
 */
public class Monitor implements Constants, VM_Callbacks.ExitMonitor {

  /**
   * Register the exit monitor at boot time.
   */
  public static void boot() {
    VM_Callbacks.addExitMonitor(new Monitor());
  }

  /**
   * The VM is about to exit.  Notify the plan.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
    Selected.Plan.get().notifyExit(value);
  }
}
