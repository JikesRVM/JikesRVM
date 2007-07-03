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
package org.jikesrvm.scheduler;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Object specifying a process to wait for,
 * and for recording its exit status.
 *
 * @see VM_ThreadProcessWaitQueue
 */
@Uninterruptible
public class VM_ThreadProcessWaitData extends VM_ThreadEventWaitData {

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
