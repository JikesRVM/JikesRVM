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
package org.jikesrvm.scheduler.greenthreads;

import org.vmmagic.pragma.Uninterruptible;

/**
 * Object specifying a process to wait for,
 * and for recording its exit status.
 *
 * @see ThreadProcessWaitQueue
 */
@Uninterruptible
public final class ThreadProcessWaitData extends ThreadEventWaitData {

  /** Process ID of process being waited for. */
  final int pid;

  /** Set to true if the process has finished. */
  public boolean finished;

  /** Exit status of the process (if finished). */
  public int exitStatus;

  /**
   * Constructor.
   * @param maxWaitNano timeout value for wait, or negative
   *   if there is no timeout
   * @param pid process ID of process being waited for
   */
  ThreadProcessWaitData(int pid, long maxWaitNano) {
    super(maxWaitNano);
    this.pid = pid;
  }

  /**
   * Inform given <code>ThreadEventWaitQueue</code> of this object's
   * concrete class type.
   */
  void accept(ThreadEventWaitDataVisitor visitor) {
    visitor.visitThreadProcessWaitData(this);
  }
}
