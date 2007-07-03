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

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * See VM_Proxy
 */
@Uninterruptible
final class VM_ProxyWaitingQueue extends VM_AbstractThreadQueue {

  private VM_Proxy tail;
  private VM_Proxy head;

  /**
   * Are any proxies on the queue?
   */
  boolean isEmpty() {
    return (head == null);
  }

  /**
   * Put proxy for this thread on the queue.
   * Since a processor lock is held, the proxy cannot be created here.
   * Instead, it is cached in the proxy field of the thread.
   */
  void enqueue(VM_Thread t) {
    enqueue(t.proxy);
  }

  /**
   * Add the proxy for a thread to tail of queue.
   */
  void enqueue(VM_Proxy p) {
    if (head == null) {
      head = p;
    } else {
      tail.waitingNext = p;
    }
    tail = p;
  }

  /**
   * Remove thread from head of queue.
   * @return the thread (null --> queue is empty)
   */
  VM_Thread dequeue() {
    while (head != null) {
      VM_Proxy p = head;
      head = head.waitingNext;
      if (head == null) tail = null;
      VM_Thread t = p.unproxy();
      if (t != null) return t;
    }
    return null;
  }

  /**
   * Number of items on queue (an estimate: queue is not locked during the scan).
   */
  int length() {
    int i = 0;
    VM_Proxy p = head;
    while (p != null) {
      i = i + 1;
      p = p.waitingNext;
    }
    return i;
  }

  // For debugging.
  //
  boolean contains(VM_Thread t) {
    VM_Proxy p = head;
    while (p != null) {
      if (p.patron == t) return true;
      p = p.waitingNext;
    }
    return false;
  }

  void dump() {
    for (VM_Proxy p = head; p != null; p = p.waitingNext) {
      if (p.patron != null) p.patron.dump();
    }
    VM.sysWrite("\n");
  }

}
