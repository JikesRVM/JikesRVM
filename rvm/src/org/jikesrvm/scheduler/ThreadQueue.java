/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.scheduler;

import org.jikesrvm.VM;
import org.vmmagic.pragma.NonMoving;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.jikesrvm.runtime.Magic;

/**
 * An unsynchronized queue data structure for Threads. The current uses are all
 * where there is some other lock that already protects the queue.
 */
@Uninterruptible
@NonMoving
public class ThreadQueue {
  protected static final boolean trace = false;

  @Untraced RVMThread head;

  @Untraced RVMThread tail;

  public ThreadQueue() {
  }

  public boolean isEmpty() {
    return head == null;
  }

  public void enqueue(RVMThread t) {
    if (trace) {
      VM.sysWriteln("enqueueing ", t.getThreadSlot(), " onto ",
          Magic.objectAsAddress(this));
    }
    if (VM.VerifyAssertions)
      VM._assert(t.queuedOn == null);
    t.next = null;
    if (tail == null) {
      head = t;
    } else {
      tail.next = t;
    }
    tail = t;
    t.queuedOn = this;
  }

  public RVMThread peek() {
    return head;
  }

  public RVMThread dequeue() {
    RVMThread result = head;
    if (result != null) {
      head = result.next;
      if (head == null) {
        tail = null;
      }
      if (VM.VerifyAssertions)
        VM._assert(result.queuedOn == this);
      result.next = null;
      result.queuedOn = null;
    }
    if (trace) {
      if (result == null) {
        VM.sysWriteln("dequeueing null from ", Magic.objectAsAddress(this));
      } else {
        VM.sysWriteln("dequeueing ", result.getThreadSlot(), " from ",
            Magic.objectAsAddress(this));
      }
    }
    return result;
  }

  /**
   * Private helper. Gets the next pointer of cur unless cur is {@code null}, in which
   * case it returns head.
   */
  private RVMThread getNext(RVMThread cur) {
    if (cur == null) {
      return head;
    } else {
      return cur.next;
    }
  }

  /**
   * Private helper. Sets the next pointer of cur to value unless cur is {@code null},
   * in which case it sets head. Also sets tail as appropriate.
   */
  private void setNext(RVMThread cur, RVMThread value) {
    if (cur == null) {
      if (tail == head) {
        tail = value;
      }
      head = value;
    } else {
      if (cur == tail) {
        tail = value;
      }
      cur.next = value;
    }
  }

  /**
   * Remove the given thread from the queue if the thread is still on the queue.
   * Does nothing (and returns in O(1)) if the thread is not on the queue. Also
   * does nothing (and returns in O(1)) if the thread is on a different queue.
   */
  public boolean remove(RVMThread t) {
    if (t.queuedOn != this)
      return false;
    if (trace) {
      VM.sysWriteln("removing ", t.getThreadSlot(), " from ",
          Magic.objectAsAddress(this));
    }
    for (RVMThread cur = null; cur != tail; cur = getNext(cur)) {
      if (getNext(cur) == t) {
        if (trace) {
          VM.sysWriteln("found!  before:");
          dump();
        }
        setNext(cur, t.next);
        if (tail == t) {
          tail = cur;
        }
        if (trace) {
          VM.sysWriteln("after:");
          dump();
        }
        t.next = null;
        t.queuedOn = null;
        return true;
      }
    }
    VM.sysWriteln("Could not remove Thread #", t.getThreadSlot(),
        " from queue!");
    dump();
    VM._assert(VM.NOT_REACHED);
    return false; // make javac happy
  }

  public boolean isQueued(RVMThread t) {
    return t.queuedOn == this;
  }

  public void dump() {
    boolean pastFirst = false;
    for (RVMThread t = head; t != null; t = t.next) {
      if (pastFirst) {
        VM.sysWrite(" ");
      }
      t.dump();
      pastFirst = true;
    }
    VM.sysWrite("\n");
    if (head != null) {
      VM.sysWriteln("head: ", head.getThreadSlot());
    } else {
      VM.sysWriteln("head: null");
    }
    if (tail != null) {
      VM.sysWriteln("tail: ", tail.getThreadSlot());
    } else {
      VM.sysWriteln("tail: null");
    }
  }
}
