/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * To implement timed waits, a thread may need to be (logically) 
 * on two queues: a waiting queue and a (the) wakeup queue.  To
 * facilitate this, a proxy represents the thread on such queues.
 * Unlike a thread, which can be on at most one queue, a proxy 
 * can be on both a waiting queue and a wakeup queue.
 *
 * Potential race condition: a thread must not be simultaneously
 * removed from both queues and scheduled twice.  lock prevents this.
 *
 * Initial implementation by Susan Flynn Hummel.  Revised to support
 * interrupt() by Bowen Alpern
 *
 * @author Susan Flynn Hummel
 * @author Bowen Alpern
 */
final class VM_Proxy implements Uninterruptible {
  
  VM_Thread        patron;
  VM_Proxy         waitingNext;
  VM_Proxy         wakeupNext;
  long             wakeupCycle;
  VM_ProcessorLock lock = new VM_ProcessorLock();
 
  // Create a proxy for a thread on a waiting queue
  //
  VM_Proxy (VM_Thread t) {
    patron = t;
  }
  
  // Create a proxy for a thread on a wakeup queue 
  // (may be on a waiting queue also)
  //
  VM_Proxy (VM_Thread t, long cycles) {
    patron = t;
    wakeupCycle = cycles;
  }
  
  // Remove the thread from the queue
  // null means the thread has already been scheduled (ignore)
  //
  VM_Thread unproxy () {
    if (patron == null) return null;
    lock.lock(); // make sure only one VP schedules the patron of this proxy
    VM_Thread t = patron;
    patron = null;
    if (t != null) t.proxy = null;
    lock.unlock();
    return t;
  }
 
}

