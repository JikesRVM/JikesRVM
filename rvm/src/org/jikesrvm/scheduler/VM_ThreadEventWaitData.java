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
 * Base class for objects specifying an event being waited for
 * while a <code>VM_Thread</code> is on a {@link VM_ThreadEventWaitQueue}.
 * Subclasses <em>must</em> directly implement the
 * {@link Uninterruptible} interface.
 */
@Uninterruptible
public abstract class VM_ThreadEventWaitData implements VM_ThreadEventConstants {

  /**
   * Timestamp at which time the thread should return from its
   * wait if the event it is waiting for has not occurred.
   */
  long maxWaitCycle;

  /**
   * Flags describing state and outcome of wait.
   * See {@link VM_ThreadEventConstants}.
   */
  int waitFlags;

  /**
   * Constructor.
   * @param maxWaitCycle the timestamp when the wait should end
   */
  public VM_ThreadEventWaitData(long maxWaitCycle) {
    this.maxWaitCycle = maxWaitCycle;
    this.waitFlags = WAIT_PENDING;
  }

  /**
   * Accept a {@link VM_ThreadEventWaitQueue} to inform it
   * of the actual type of this object.
   */
  public abstract void accept(VM_ThreadEventWaitDataVisitor visitor);

  /**
   * Is the object marked as having timed out?
   */
  public boolean timedOut() {
    return (this.waitFlags & WAIT_TIMEOUT) != 0;
  }
}
