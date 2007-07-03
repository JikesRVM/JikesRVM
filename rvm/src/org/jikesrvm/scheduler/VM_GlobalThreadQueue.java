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

import org.vmmagic.pragma.Uninterruptible;

/**
 * A global queue of VM_Threads.
 *
 * For the transferQueues (more efficient implementation of length()).
 */
@Uninterruptible
public final class VM_GlobalThreadQueue extends VM_ThreadQueue {

  @SuppressWarnings("unused")
  private final VM_ProcessorLock mutex; // TODO check that mutex is held when manipulating this queue.
  private int length;

  public VM_GlobalThreadQueue(VM_ProcessorLock mutex) {
    super();
    this.mutex = mutex;
  }

  public void enqueueHighPriority(VM_Thread t) {
    length++;
    super.enqueueHighPriority(t);
  }

  public void enqueue(VM_Thread t) {
    length++;
    super.enqueue(t);
  }

  public VM_Thread dequeue() {
    if (length == 0) return null;
    VM_Thread t = super.dequeue();
    if (t == null) return null;
    length--;
    return t;
  }

  public VM_Thread dequeueGCThread(VM_ProcessorLock qlock) {
    if (length == 0) return null;
    VM_Thread t = super.dequeueGCThread(qlock);
    if (t == null) return null;
    length--;
    return t;
  }

  // Number of items on queue .
  //
  public int length() {
    return length;
  }

}
