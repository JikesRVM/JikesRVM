/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * A queue to handle threads that have died.
 * We keep references to threads whose "phantom stacks" are in use by a dispatcher
 * and recycle thread id's whose stacks have been abandoned.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
final class VM_DeadThreadQueue extends VM_AbstractThreadQueue implements VM_Uninterruptible {

  private VM_ThreadQueue queue;
  private int            length;
  
  VM_DeadThreadQueue(int id) {
    queue = new VM_ThreadQueue(id);
  }

  boolean isEmpty () {
    return queue.isEmpty();
  }

  void enqueue (VM_Thread deadThread) {

   // discard all threads whose stacks have been abandoned since the last time we looked
   //
   for (int i = 0, n = length; i < n; ++i)
      {
      VM_Thread t = queue.dequeue();
      if (t.beingDispatched)
         {
         queue.enqueue(t);
         continue;
         }
      t.releaseThreadSlot(); // recycle
      --length;
      }

   // add on the one that died
   //
   if (VM.VerifyAssertions) VM_Scheduler._assert(deadThread.beingDispatched == true);
   queue.enqueue(deadThread);
   ++length;
  }

  VM_Thread dequeue () { 
    if (VM.VerifyAssertions) VM_Scheduler._assert(VM.NOT_REACHED); // should not be called
    return null;
  }
  
  int length () {
    return length;
  }
  
  boolean contains(VM_Thread x)
     {
     return queue.contains(x);
     }

  void dump () {
    queue.dump();
  }
  
}
