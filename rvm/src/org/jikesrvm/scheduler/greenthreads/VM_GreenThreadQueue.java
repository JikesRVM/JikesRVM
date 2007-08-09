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

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_ProcessorLock;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * A queue of VM_Threads
 */
@Uninterruptible
public class VM_GreenThreadQueue extends VM_AbstractThreadQueue {

  /**
   * First thread on list.
   */
  protected VM_GreenThread head;

  /**
   * Last thread on the list.
   */
  protected VM_GreenThread tail;

  /**
   * Are any threads on the queue?
   */
  @Override
  public boolean isEmpty() {
    return head == null;
  }

  /**
   * Atomic test to determine if any threads are on the queue.
   *    Note: The test is required for native idle threads
   */
  boolean atomicIsEmpty(VM_ProcessorLock lock) {
    boolean r;

    lock.lock("atomic is empty");
    r = (head == null);
    lock.unlock();
    return r;
  }

  /** Add a thread to head of queue. */
  public void enqueueHighPriority(VM_GreenThread t) {
    if (VM.VerifyAssertions) VM._assert(t.getNext() == null); // not currently on any other queue
    t.setNext(head);
    head = t;
    if (tail == null) {
      tail = t;
    }
  }

  /** Add a thread to tail of queue. */
  @Override
  @UninterruptibleNoWarn
  public void enqueue(VM_GreenThread t) {
    // not currently on any other queue
    if (VM.VerifyAssertions && t.getNext() != null) {
      VM.sysWrite("Thread sitting on >1 queue: ");
      VM.sysWriteln(VM_Magic.getObjectType(t).getDescriptor());
      VM._assert(false);
    }
    // not dead
    if (VM.VerifyAssertions) VM._assert(t.isQueueable());
    if (head == null) {
      head = t;
    } else {
      tail.setNext(t);
    }
    tail = t;
  }

  /**
   * Remove a thread from the head of the queue.
   * @return the thread (null --> queue is empty)
   */
  @Override
  public VM_GreenThread dequeue() {
    VM_GreenThread t = head;
    if (t == null) {
      return null;
    }
    head = t.getNext();
    t.setNext(null);
    if (head == null) {
      tail = null;
    }
    if (VM.VerifyAssertions) VM._assert(t.isQueueable());
    return t;
  }

  /**
   * Dequeue the CollectorThread, if any, from this queue. If qlock != null
   * protect by lock.
   * @return The garbage collector thread. If no thread found, return null.
   */
  VM_GreenThread dequeueGCThread(VM_ProcessorLock qlock) {
    if (qlock != null) qlock.lock("dequeue GC thread");
    VM_GreenThread currentThread = head;
    if (head == null) {
      if (qlock != null) qlock.unlock();
      return null;
    }
    VM_GreenThread nextThread = head.getNext();

    if (currentThread.isGCThread()) {
      head = nextThread;
      if (head == null) {
        tail = null;
      }
      currentThread.setNext(null);
      if (qlock != null) qlock.unlock();
      return currentThread;
    }

    while (nextThread != null) {
      if (nextThread.isGCThread()) {
        currentThread.setNext(nextThread.getNext());
        if (nextThread == tail) {
          tail = currentThread;
        }
        nextThread.setNext(null);
        if (qlock != null) qlock.unlock();
        return nextThread;
      }
      currentThread = nextThread;
      nextThread = nextThread.getNext();
    }

    return null;
  }

  /**
   * Number of items on queue (an estimate only: we do not lock the queue during
   * this scan.)
   */
  @Override
  public int length() {
    int length = 0;
    for (VM_GreenThread t = head; t != null; t = t.getNext()) {
      length += 1;
    }
    return length;
  }

  /** Debugging. */
  public boolean contains(VM_GreenThread x) {
    for (VM_GreenThread t = head; t != null; t = t.getNext()) {
      if (t == x) return true;
    }
    return false;
  }

  public boolean containsGCThread() {
    for (VM_GreenThread t = head; t != null; t = t.getNext()) {
      if (t.isGCThread()) return true;
    }
    return false;
  }

  public void dump() {
    // We shall space-separate them, for compactness.
    // I hope this is a good decision.
    boolean pastFirst = false;
    for (VM_GreenThread t = head; t != null; t = t.getNext()) {
      if (pastFirst) {
        VM.sysWrite(" ");
      }
      t.dump();
      pastFirst = true;
    }
    VM.sysWrite("\n");
  }
}
