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
import org.jikesrvm.runtime.VM_Time;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A queue of VM_Proxys prioritized by their thread wakeup times.
 * based on VM_WakeupQueue (14 October 1998 Bowen Alpern)
 */
@Uninterruptible
public final class VM_ThreadProxyWakeupQueue extends VM_AbstractThreadQueue {

  /** first thread on list */
  private VM_ThreadProxy head;

  /**
   * Are any proxies on the queue?
   */
  @Override
  public boolean isEmpty() {
    return head == null;
  }

  /**
   * Is the head of the queue (the thread set to wake up first) ready to be
   * restarted?
   */
  public boolean isReady() {
    VM_ThreadProxy temp = head;
    return ((temp != null) && (VM_Time.nanoTime() >= temp.getWakeupNano()));
  }

  /**
   * Put proxy for this thread on the queue.
   * Since a processor lock is held, the proxy cannot be created here.
   * Instead, it is cached in the proxy field of the thread.
   */
  @Override
  public void enqueue(VM_GreenThread t) {
    enqueue(t.threadProxy);
  }

  /**
   * Add the proxy for a thread in a place determined by its wakeup time
   */
  public void enqueue(VM_ThreadProxy p) {
    if (p != null) {
      p.mutex.lock("Enqueueing proxy");
      if (p.getPatron() != null) {
        if(VM.VerifyAssertions) VM._assert(p.getPatron().isQueueable());
        VM_ThreadProxy previous = null;
        VM_ThreadProxy current = head;
        // skip proxies with earlier wakeup timestamps
        while (current != null && current.getWakeupNano() <= p.getWakeupNano()) {
          previous = current;
          current = current.getWakeupNext();
        }
        // insert p
        if (previous == null) {
          head = p;
        } else {
          previous.setWakeupNext(p);
        }
        p.setWakeupNext(current);
      }
      p.mutex.unlock();
    }
  }

  /**
   * Remove a thread from the queue if there's one ready to wake up "now".
   * Returned: the thread (null --> nobody ready to wake up)
   */
  @Override
  public VM_GreenThread dequeue() {
    long currentNano = VM_Time.nanoTime();
    while (head != null) {
      if (currentNano < head.getWakeupNano()) return null;
      VM_ThreadProxy p = head;
      head = head.getWakeupNext();
      p.setWakeupNext(null);
      VM_GreenThread t = p.unproxy();
      if (t != null) return t;
    }
    return null;
  }

  /**
   * Number of items on queue (an estimate: queue is not locked during the
   * scan).
   */
  @Override
  public int length() {
    if (head == null) return 0;
    int length = 1;
    for (VM_ThreadProxy p = head; p != null; p = p.getWakeupNext()) {
      length += 1;
    }
    return length;
  }

  // Debugging.
  //
  public boolean contains(VM_GreenThread t) {
    for (VM_ThreadProxy p = head; p != null; p = p.getWakeupNext()) {
      if (p.getPatron() == t) return true;
    }
    return false;
  }

  public void dump() {
    if (head != null) {
      VM.sysWrite(" nowNano=", VM_Time.nanoTime());
      VM.sysWrite(" ");
      for (VM_ThreadProxy p = head; p != null; p = p.getWakeupNext()) {
        if (p.getPatron() != null) {
          p.getPatron().dump();
          VM.sysWrite("(wakeupNano=", p.getWakeupNano()); VM.sysWrite(") ");
        }
      }
    }
    VM.sysWrite("\n");
  }
}
