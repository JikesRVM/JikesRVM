/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * A queue to handle a set of  virtual processors  
 *  For Example A Native Virtual Processor VM_Thread has terminated and
 *  can be reused.
 *    When a normal thread (VM_Thread) first does a call to native 
 *      a special virtual processor and pthread are created just for that 
 *      VM_Thread they run together as a pair until the VMThread terminates
 *      then the VP and pthread are enqueued onto a DeadVP queue
 *      until some subsequent VM_Thread first performs a calltonative
 *      then a request is made to reuse a previous VP and pthread
 *    so the result is that the VP and pthread are recycled
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
final class VM_ProcessorQueue implements Uninterruptible {

  /**
   * first thread on list
   */
  private VM_Processor head;   
  /**
   * last thread on list
   */
  private VM_Processor tail;   
 
  /**
   * is the queue empty
   */ 
  boolean isEmpty () {
   return head == null;
  }

  /**
   * Add a VP to tail of queue.
   */ 
  synchronized void enqueue (VM_Processor p) throws InterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(p.next == null); // not currently on any other queue
    if (head == null)
      head = p;
    else
      tail.next = p;
    tail = p;
  }

  /**
   * Remove VP from head of queue.
   * @return the thread (null --> queue is empty)
   */ 
  synchronized VM_Processor dequeue () throws InterruptiblePragma {
    VM_Processor p = head;
    if (p == null)
       return null;
    head = p.next;
    p.next = null;
    if (head == null)
      tail = null;
    return p;
  }

 
  /**
   * Number of items on queue (an estimate: queue is not locked during the scan).
   */ 
  int length() {
    int length = 0;
    for (VM_Processor p = head; p != null; p = p.next)
      length += 1;
    return length;
  }

  /**
   * dump the vp queue
   */ 
  void dump () throws InterruptiblePragma {
    VM.sysWrite("Virtual Processor Dead Queue\n");
    for (VM_Processor p = head; p != null; p = p.next)
      p.dumpProcessorState();
    VM.sysWrite("\n");
  }
}
