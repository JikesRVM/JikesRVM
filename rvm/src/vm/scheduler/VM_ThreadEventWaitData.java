/*
 * (C) Copyright IBM Corp. 2002
 */
// $Id$

package com.ibm.JikesRVM;

/**
 * Base class for objects specifying an event being waited for
 * while a <code>VM_Thread</code> is on a {@link VM_ThreadEventWaitQueue}.
 * Subclasses <em>must</em> directly implement the
 * {@link VM_Uninterruptible} interface.
 *
 * @author David Hovemeyer
 */
public abstract class VM_ThreadEventWaitData
  implements VM_Uninterruptible, VM_ThreadEventConstants {

  /**
   * Timestamp at which time the thread should return from its
   * wait if the event it is waiting for has not occurred.
   */ 
  double maxWaitTime;

  /**
   * Flags describing state and outcome of wait.
   * See {@link VM_ThreadEventConstants}.
   */
  int waitFlags;

  /**
   * Constructor.
   * @param maxWaitTime the timestamp when the wait should end
   */
  public VM_ThreadEventWaitData(double maxWaitTime) {
    this.maxWaitTime = maxWaitTime;
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
