/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * Object specifying a process to wait for,
 * and for recording its exit status.
 *
 * @author David Hovemeyer
 * @see VM_ThreadProcessWaitQueue
 */
public class VM_ThreadProcessWaitData
  extends VM_ThreadEventWaitData
  implements Uninterruptible {

  /** Process ID of process being waited for. */
  int pid;

  /** Set to true if the process has finished. */
  boolean finished;

  /** Exit status of the process (if finished). */
  int exitStatus;

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
