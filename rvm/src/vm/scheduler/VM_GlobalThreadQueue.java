/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A global queue of VM_Threads.
 *
 * For the transferQueues (more efficient implementation of length()).
 *
 * @author Bowen Alpern
 * @date 30 August 1998 
 */
final class VM_GlobalThreadQueue extends VM_ThreadQueue implements VM_Uninterruptible {

  private VM_ProcessorLock mutex; // TODO check that mutex is heald when manipulating this queue.
  private int length;
  
  VM_GlobalThreadQueue(int id, VM_ProcessorLock mutex) {
    super(id);
    this.mutex = mutex;
  }
  
  void enqueueHighPriority (VM_Thread t) {
    length++;
    super.enqueueHighPriority(t);
  }
  
  void enqueue (VM_Thread t) {
    length++;
    super.enqueue(t);
  }
  
  VM_Thread dequeue () {
    if (length == 0) return null;
    VM_Thread t = super.dequeue();
    if (t == null) return null;
    length--;
    return t;
  }
  
  VM_Thread dequeue (int processorID) {
    if (length == 0) return null;
    VM_Thread t = super.dequeue(processorID);
    if (t == null) return null;
    length--;
    return t;
  }
  
  VM_Thread dequeueGCThread (VM_ProcessorLock qlock) {
    if (length == 0) return null;
    VM_Thread t = super.dequeueGCThread(qlock);
    if (t == null) return null;
    length--;
    return t;
  }

  // Number of items on queue .
  //
  int length() {
    return length;
  }

}
