/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of
 * address pairs
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class SharedQueue extends Queue implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   *
   */
  SharedQueue(RawPageAllocator rpa, int arity) {
    this.rpa = rpa;
    this.arity = arity;
    lock = new int[1];
    completionFlag = false;
  }

  public final boolean complete() {
    return completionFlag;
  }

  public final int getArity() throws VM_PragmaInline { return arity; }

  public final void enqueue(VM_Address buf, int arity, boolean toTail) {
    if (VM.VerifyAssertions) VM._assert(arity == this.arity);

    lock();
    if (toTail) {
      // Add to the tail of the queue
      setNext(buf, VM_Address.zero());
      if (tail.isZero())
	head = buf;
      else
	setNext(tail, buf);
      tail = buf;
    } else {
      // Add to the head of the queue
      if (head.isZero())
	tail = buf;
      setNext(buf, head);
      head = buf;
    } 
    if (VM.VerifyAssertions) {
      bufsenqueued++;
      VM._assert(bufsenqueued == debugQueueLength());
    }
    unlock();
  }

  public final VM_Address dequeue(int arity) {
    if (VM.VerifyAssertions) VM._assert(arity == this.arity);
    return dequeue(false);
  }

  public final VM_Address dequeueAndWait(int arity) {
    if (VM.VerifyAssertions) VM._assert(arity == this.arity);
    VM_Address buf = dequeue(false);
    while (buf.isZero() && !completionFlag) {
      //      int x = spin();
      buf = dequeue(true);
    }
    return buf;  
  }

  public final void reset() {
    numClientsWaiting = 0;
    completionFlag = false;
    VM._assert(head.isZero());
    VM._assert(tail.isZero());
  }

  public final void newClient() {
    numClients++;
  }

  public final VM_Address alloc() throws VM_PragmaInline {
    VM_Address rtn = rpa.alloc(PAGES_PER_BUFFER);
    if (VM.VerifyAssertions) VM._assert(rtn.EQ(bufferStart(rtn)));
    return rtn;
  }

  public final void free(VM_Address buf) throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(buf.EQ(bufferStart(buf)) && !buf.isZero());
    rpa.free(buf);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private instance methods and fields
  //
  private RawPageAllocator rpa;
  private int arity;
  private boolean completionFlag;
  private int numClients;
  private int numClientsWaiting;
  private VM_Address head;
  private VM_Address tail;
  private int bufsenqueued;
  private int lock[];

  
  private final VM_Address dequeue(boolean waiting) {
    lock();
    VM_Address rtn;
    if (head.isZero()) {
      if (VM.VerifyAssertions) VM._assert(tail.isZero());
      // no buffers available
      if (!waiting) {
	numClientsWaiting++;
	if (numClientsWaiting == numClients)
	  completionFlag = true;
      }
      rtn = VM_Address.zero();
    } else {
      // dequeue the head buffer
      rtn = head;
      head = getNext(head);
      if (tail.EQ(rtn)) {
	tail = VM_Address.zero();
	if (VM.VerifyAssertions) VM._assert(head.isZero());
      }
      if (VM.VerifyAssertions) bufsenqueued--;
      if (waiting)
	numClientsWaiting--;
    }
    unlock();
    return rtn;
  }

  /**
   * Set the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param bufRef The buffer whose next field is to be set.
   * @param next The reference to which next should point.
   */
  private static final void setNext(VM_Address buf, VM_Address next) {
    VM_Magic.setMemoryWord(buf, next.toInt());
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param bufRef The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  private final VM_Address getNext(VM_Address buf) {
    return VM_Address.fromInt(VM_Magic.getMemoryWord(buf));
  }

  /**
   * Establish the number of buffers in the work queue (for debugging
   * purposes).
   *
   * @return The number of buffers in the work queue.
   */
  private final int debugQueueLength() {
    VM_Address top = head;
    int l = 0;
    while (!top.isZero()) {
      top = getNext(top);
      l++;
    }
    return l;
  }

  /**
   * Lock this shared queue.  We use one simple low-level lock to
   * synchronize access to the shared queue of buffers.
   */
  private final void lock() {
    while (!(VM_Synchronization.testAndSet(lock, 0, 1)));
    VM_Magic.isync();
  }
  
  /**
   * Release the lock.  We use one simple low-level lock to synchronize
   * access to the shared queue of buffers.
   */
  private final void unlock() {
    lock[0] = 0;
    VM_Magic.sync();
  }
}
