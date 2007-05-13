/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
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
