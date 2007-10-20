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
 * Object specifying sets of file descriptors to wait for.
 * Used as event wait data for {@link VM_ThreadEventWaitQueue#enqueue}.
 *
 *
 * @see VM_ThreadEventWaitData
 */
@Uninterruptible
public final class VM_ThreadIOWaitData extends VM_ThreadEventWaitData implements VM_ThreadIOConstants {

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
  public VM_ThreadIOWaitData(long maxWaitNano) {
    super(maxWaitNano);
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
      for (int i = 0; i < fds.length; ++i) {
        fds[i] |= FD_READY_BIT;
      }
    }
  }
}
