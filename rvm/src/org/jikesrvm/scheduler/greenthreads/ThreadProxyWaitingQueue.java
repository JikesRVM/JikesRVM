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
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Uninterruptible;

/**
 * See Proxy
 */
@Uninterruptible
public final class ThreadProxyWaitingQueue extends AbstractThreadQueue {

  /** The end of the list of waiting proxies */
  private ThreadProxy tail;
  /** The head of the list of waiting proxies */
  private ThreadProxy head;

  /**
   * Are any proxies on the queue?
   */
  @Override
  public boolean isEmpty() {
    return (head == null);
  }

  /**
   * Put proxy for this thread on the queue.
   * Since a processor lock is held, the proxy cannot be created here.
   * Instead, it is cached in the proxy field of the thread.
   */
  @Override
  public void enqueue(GreenThread t) {
    enqueue(t.threadProxy);
  }

  /**
   * Add the proxy for a thread to tail of queue.
   */
  public void enqueue(ThreadProxy p) {
    if (head == null) {
      head = p;
    } else {
      tail.setWaitingNext(p);
    }
    tail = p;
  }

  /**
   * Remove thread from head of queue.
   * @return the thread (null --> queue is empty)
   */
  @Override
  public GreenThread dequeue() {
    while (head != null) {
      ThreadProxy p = head;
      head = head.getWaitingNext();
      if (head == null) tail = null;
      GreenThread t = p.unproxy();
      if (t != null) return t;
    }
    return null;
  }

  /**
   * Number of items on queue (an estimate: queue is not locked during the scan).
   */
  @Override
  public int length() {
    int i = 0;
    ThreadProxy p = head;
    while (p != null) {
      i = i + 1;
      p = p.getWaitingNext();
    }
    return i;
  }

  // For debugging.
  //
  boolean contains(RVMThread t) {
    ThreadProxy p = head;
    while (p != null) {
      if (p.getPatron() == t) return true;
      p = p.getWaitingNext();
    }
    return false;
  }

  void dump() {
    boolean pastFirst = false;
    for (ThreadProxy p = head; p != null; p = p.getWaitingNext()) {
      if (pastFirst) {
        VM.sysWrite(" ");
      }
      if (p.getPatron() != null) {
        p.getPatron().dump();
        pastFirst = true;
      }
    }
    VM.sysWrite("\n");
  }
}
