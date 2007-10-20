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
 * Base class for queues of VM_Threads.
 */
@Uninterruptible
public abstract class VM_AbstractThreadQueue {

  /** Are any threads on the queue? */
  public abstract boolean isEmpty();

  /** Add a thread to tail of queue. */
  public abstract void enqueue(VM_GreenThread t);

  /**
   * Remove thread from head of queue.
   * @return the thread (null --> queue is empty)
   */
  public abstract VM_GreenThread dequeue();

  /**
   * Number of items on queue (an estimate: queue is not locked during the scan).
   */
  public abstract int length();
}
