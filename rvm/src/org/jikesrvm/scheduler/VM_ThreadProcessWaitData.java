/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */

package org.jikesrvm.scheduler;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Object specifying a process to wait for,
 * and for recording its exit status.
 *
 * @see VM_ThreadProcessWaitQueue
 */
@Uninterruptible
public class VM_ThreadProcessWaitData
    extends VM_ThreadEventWaitData {

  /** Process ID of process being waited for. */
  int pid;

  /** Set to true if the process has finished. */
  public boolean finished;

  /** Exit status of the process (if finished). */
  public int exitStatus;

  /**
   * Constuctor.
   * @param maxWaitCycle timeout value for wait, or negative
   *   if there is no timeout
   * @param pid process ID of process being waited for
   */
  public VM_ThreadProcessWaitData(int pid, long maxWaitCycle) {
    super(maxWaitCycle);
    this.pid = pid;
  }

  /**
   * Inform given <code>VM_ThreadEventWaitQueue</code> of this object's
   * concrete class type.
   */
  public void accept(VM_ThreadEventWaitDataVisitor visitor) {
    visitor.visitThreadProcessWaitData(this);
  }
}
