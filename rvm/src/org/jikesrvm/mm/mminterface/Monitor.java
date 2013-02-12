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
package org.jikesrvm.mm.mminterface;

import org.jikesrvm.Callbacks;
import org.mmtk.utility.Constants;

/**
 * This class allows JMTk to register call backs with Callbacks.
 */
public class Monitor implements Constants, Callbacks.ExitMonitor {

  /**
   * Register the exit monitor at boot time.
   */
  public static void boot() {
    Callbacks.addExitMonitor(new Monitor());
  }

  /**
   * The VM is about to exit.  Notify the plan.
   *
   * @param value The exit value
   */
  @Override
  public void notifyExit(int value) {
    Selected.Plan.get().notifyExit(value);
  }
}
