/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.jikesrvm.memorymanagers.mminterface;

import org.jikesrvm.VM_Callbacks;
import org.mmtk.utility.Constants;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;

/**
 * This class allows JMTk to register call backs with VM_Callbacks.
 */
@Uninterruptible
public class Monitor
    implements Constants, VM_Callbacks.ExitMonitor {

  /**
   * Register the exit monitor at boot time.
   */
  @Interruptible
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
