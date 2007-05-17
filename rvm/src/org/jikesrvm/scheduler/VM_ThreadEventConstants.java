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

/**
 * Constants specifying the state of a {@link VM_Thread} waiting
 * on a {@link VM_ThreadEventWaitQueue}.
 *
 *
 * @see VM_ThreadEventWaitQueue
 */
public interface VM_ThreadEventConstants {
  /**
   * Thread is waiting on the queue.
   */
  int WAIT_PENDING = 0;

  /**
   * Set if thread is suspended while executing native code.
   */
  int WAIT_NATIVE = 1;

  /**
   * Set if thread has been marked to be taken off the wait queue.
   * Possible reasons:
   * <ul>
   * <li> the event it was waiting for occurred,
   * <li> the wait timed out, or
   * <li> the thread was interrupted
   * </ul>
   */
  int WAIT_FINISHED = 2;

  /**
   * The event wait timed out.
   */
  int WAIT_TIMEOUT = 4;

  /**
   * The thread was interrupted before the event or the timeout occurred.
   */
  int WAIT_INTERRUPTED = 8;

  /**
   * Used to specify that a wait should block indefinitely
   * (i.e., no timeout).
   */
  long WAIT_INFINITE = -1;
}
