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
  private final long maxWaitNano;
  /** Accessor for maxWaitCycle */
  long getMaxWaitNano() {
    return maxWaitNano;
  }
  /*
   * Flags describing state and outcome of wait.
   */
  /**
   * Set if thread is suspended while executing native code.
   */
  private boolean _native;
  /**
   * Set if thread has been marked to be taken off the wait queue.
   */
  private boolean finished;
  /**
   * The event wait timed out.
   */
  private boolean timedout;
  /**
   * The thread was interrupted before the event or the timeout occurred.
   */
  private boolean interrupted;
  
  /** The thread is waiting on a queue (NB state at construction)*/
  boolean isPending() {
    return !_native && !finished && !timedout && !interrupted;
  }
  /** Was the thread suspended while executing native code? */
  boolean isNative() {
    return _native;
  }
  /** The thread was suspended while executing native code. */
  void setNative() {
    _native = true;
  }
  /** Is the thread marked to be taken off the wait queue? */
  boolean isFinished() {
    return finished;
  }
  /** Set that the thread should be taken off the wait queue. */
  void setFinished() {
    finished = true;
  }
  /** Clear that the thread should be taken off the wait queue. */
  void clearFinished() {
    finished = false;
  }
  /** Did the event wait time out? */
  public boolean isTimedOut() {
    return timedout;
  }
  /** Was the thread was interrupted before the event or the timeout occurred? */
  boolean isInterrupted() {
    return interrupted;
  }
  /** The thread is finished from an interrupt */
  void setFinishedAndInterrupted() {
    finished = true;
    interrupted = true;
  }
  /** The thread is finished from a timeout */
  void setFinishedAndTimeout() {
    finished = true;
    timedout = true;
  }

  /**
   * Constructor.
   * @param maxWaitNano the timestamp when the wait should end
   */
  VM_ThreadEventWaitData(long maxWaitNano) {
    // assert isPending();
    this.maxWaitNano = maxWaitNano;
  }

  /**
   * Accept a {@link VM_ThreadEventWaitQueue} to inform it
   * of the actual type of this object.
   */
  abstract void accept(VM_ThreadEventWaitDataVisitor visitor);
}
