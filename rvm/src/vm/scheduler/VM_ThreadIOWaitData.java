/*
 * (C) Copyright IBM Corp. 2002
 */
// $Id$

package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * Object specifying sets of file descriptors to wait for.
 * Used as event wait data for {@link VM_ThreadEventWaitQueue#enqueue}.
 *
 * @author David Hovemeyer
 *
 * @see VM_ThreadEventWaitData
 */
public class VM_ThreadIOWaitData
  extends VM_ThreadEventWaitData
  implements Uninterruptible, VM_ThreadIOConstants  {

  public int[] readFds;
  public int[] writeFds;
  public int[] exceptFds;

  // Offsets of the corresponding entries in VM_ThreadIOQueue's
  // file descriptor arrays
  public int readOffset, writeOffset, exceptOffset;

  /**
   * Constructor.
   * @param maxWaitCycle the timestamp when the wait should end
   */
  public VM_ThreadIOWaitData(long maxWaitCycle) {
    super(maxWaitCycle);
  }

  /**
   * Accept a {@link VM_ThreadEventWaitQueue} to inform it
   * of the actual type of this object.
   */
  public void accept(VM_ThreadEventWaitDataVisitor visitor) {
    visitor.visitThreadIOWaitData(this);
  }

  /**
   * Mark all file descriptors as ready.
   * This is useful when we need to circumvent the IO wait mechanism,
   * such as when the VM is shutting down (and we can't rely on
   * thread switching).
   */
  public void markAllAsReady() {
    markAsReady(readFds);
    markAsReady(writeFds);
    markAsReady(exceptFds);
  }

  private void markAsReady(int[] fds) {
    if (fds != null) {
      for (int i = 0; i < fds.length; ++i)
        fds[i] |= FD_READY_BIT;
    }
  }
}
