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
import org.jikesrvm.runtime.VM_Time;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A queue of VM_Proxys prioritized by their thread wakeup times.
 * based on VM_WakeupQueue (14 October 1998 Bowen Alpern)
 */
@Uninterruptible
final class VM_ProxyWakeupQueue extends VM_AbstractThreadQueue {

  private VM_Proxy head; // first thread on list

  boolean isEmpty() {
    return head == null;
  }

  boolean isReady() {
    VM_Proxy temp = head;
    return ((temp != null) && (VM_Time.cycles() >= temp.wakeupCycle));
  }

  void enqueue(VM_Thread t) {
    enqueue(t.proxy);
  }

  void enqueue(VM_Proxy p) {
    VM_Proxy previous = null;
    VM_Proxy current = head;
    while (current != null && current.wakeupCycle <= p.wakeupCycle) { // skip proxies with earlier wakeupCycles
      previous = current;
      current = current.wakeupNext;
    }
    // insert p
    if (previous == null) {
      head = p;
    } else {
      previous.wakeupNext = p;
    }
    p.wakeupNext = current;
  }

  // Remove a thread from the queue if there's one ready to wake up "now".
  // Returned: the thread (null --> nobody ready to wake up)
  //
  VM_Thread dequeue() {
    long currentCycle = VM_Time.cycles();
    while (head != null) {
      if (currentCycle < head.wakeupCycle) return null;
      VM_Proxy p = head;
      head = head.wakeupNext;
      p.wakeupNext = null;
      VM_Thread t = p.unproxy();
      if (t != null) return t;
    }
    return null;
  }

  // Number of items on queue (an estimate: queue is not locked during the scan).
  //
  int length() {
    if (head == null) return 0;
    int length = 1;
    for (VM_Proxy p = head; p != null; p = p.wakeupNext) {
      length += 1;
    }
    return length;
  }

  // Debugging.
  //
  boolean contains(VM_Thread t) {
    for (VM_Proxy p = head; p != null; p = p.wakeupNext) {
      if (p.patron == t) return true;
    }
    return false;
  }

  void dump() {
    for (VM_Proxy p = head; p != null; p = p.wakeupNext) {
      if (p.patron != null) p.patron.dump();
    }
    VM.sysWrite("\n");
  }

}
