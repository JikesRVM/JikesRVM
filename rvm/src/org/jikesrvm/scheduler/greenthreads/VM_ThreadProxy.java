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

import org.jikesrvm.scheduler.VM_ProcessorLock;
import org.vmmagic.pragma.Uninterruptible;

/**
 * To implement timed waits, a thread may need to be (logically) on two queues:
 * a waiting queue and a (the) wakeup queue. To facilitate this, a proxy
 * represents the thread on such queues. Unlike a thread, which can be on at
 * most one queue, a proxy can be on both a waiting queue and a wakeup queue.
 *
 * When the proxy is dequeued it nullifies its thread reference. Either
 * queue finding a proxy with a null patron knows the other queue has handled
 * it and should skip that entry.
 *
 * Potential race condition: a thread must not be simultaneously removed from
 * both queues and scheduled twice. An atomic swap prevents this.
 */
@Uninterruptible
public final class VM_ThreadProxy {
  /**
   * The real thread that is waiting on both queues or null. Null is used to
   * indicate a thread has been dequed from one or other queue. If a null proxy
   * is found on a queue then the queue will remove it.
   */
  private volatile VM_GreenThread patron;
  /**
   * Ensure atomicity of certain events
   */
  final VM_ProcessorLock mutex = new VM_ProcessorLock();
  /** When the thread is scheduled to wake up if it's on the wakeup queue */
  private final long wakeupNano;
  /** The next element in the waiting queue */
  private VM_ThreadProxy waitingNext;
  /** The next element in the wakeup queue */
  private VM_ThreadProxy wakeupNext;

  /** Create a proxy for a thread on a waiting queue */
  public VM_ThreadProxy(VM_GreenThread t) {
    patron = t;
    wakeupNano = 0;
  }

  /**
   * Create a proxy for a thread on a wakeup queue (may be on a waiting queue
   * also)
   */
  public VM_ThreadProxy(VM_GreenThread t, long nano) {
    patron = t;
    wakeupNano = nano;
  }

  /**
   * Remove the thread from the queue
   * @return null means the thread has already been scheduled (ignore)
   */
  public VM_GreenThread unproxy() {
    VM_GreenThread t = patron;
    if (t != null) {
      mutex.lock("Unproxying thread");
      t = patron;
      patron = null;
      if (t != null) {
        t.threadProxy = null;
      }
      mutex.unlock();
    }
    return t;
  }

  /** Get the thread owning the proxy */
  VM_GreenThread getPatron() {
    return patron;
  }

  /** Get the wake up time */
  long getWakeupNano() {
    return wakeupNano;
  }

  /** Get the next element in the waiting queue */
  VM_ThreadProxy getWaitingNext() {
    return waitingNext;
  }

  /** Set the next element in the waiting queue */
  void setWaitingNext(VM_ThreadProxy wn) {
    waitingNext = wn;
  }

  /** Get the next element in the wakeup queue */
  VM_ThreadProxy getWakeupNext() {
    return wakeupNext;
  }
  /** Set the next element in the wakeup queue */
  void setWakeupNext(VM_ThreadProxy wn) {
    wakeupNext = wn;
  }
}
