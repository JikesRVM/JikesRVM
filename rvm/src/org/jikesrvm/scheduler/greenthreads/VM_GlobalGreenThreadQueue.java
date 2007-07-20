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
 * A global queue of VM_GreenThreads.
 *
 * For the transferQueues (more efficient implementation of length()).
 */
@Uninterruptible
public final class VM_GlobalGreenThreadQueue extends VM_GreenThreadQueue {

  @SuppressWarnings("unused")
  private final VM_ProcessorLock mutex; // TODO check that mutex is held when manipulating this queue.
  private int length;

  public VM_GlobalGreenThreadQueue(VM_ProcessorLock mutex) {
    super();
    this.mutex = mutex;
  }

  @Override
  public void enqueueHighPriority(VM_GreenThread t) {
    length++;
    super.enqueueHighPriority(t);
  }

  @Override
  public void enqueue(VM_GreenThread t) {
    length++;
    super.enqueue(t);
  }

  @Override
  public VM_GreenThread dequeue() {
    if (length == 0) return null;
    VM_GreenThread t = super.dequeue();
    if (t == null) return null;
    length--;
    return t;
  }

  @Override
  public VM_GreenThread dequeueGCThread(VM_ProcessorLock qlock) {
    if (length == 0) return null;
    VM_GreenThread t = super.dequeueGCThread(qlock);
    if (t == null) return null;
    length--;
    return t;
  }

  /**
   * Number of items on queue .
   */
  @Override
  public int length() {
    return length;
  }
}
