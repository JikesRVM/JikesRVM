/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;

/**
 * A global queue of VM_Threads.
 *
 * For the transferQueues (more efficient implementation of length()).
 *
 * @author Bowen Alpern
 * @date 30 August 1998 
 */
public final class VM_GlobalThreadQueue extends VM_ThreadQueue implements Uninterruptible {

  private VM_ProcessorLock mutex; // TODO check that mutex is heald when manipulating this queue.
  private int length;
  
  public VM_GlobalThreadQueue(VM_ProcessorLock mutex) {
    super();
    this.mutex = mutex;
  }
  
  public void enqueueHighPriority (VM_Thread t) {
    length++;
    super.enqueueHighPriority(t);
  }
  
  public void enqueue (VM_Thread t) {
    length++;
    super.enqueue(t);
  }
  
  public VM_Thread dequeue () {
    if (length == 0) return null;
    VM_Thread t = super.dequeue();
    if (t == null) return null;
    length--;
    return t;
  }
  
  public VM_Thread dequeueGCThread (VM_ProcessorLock qlock) {
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
