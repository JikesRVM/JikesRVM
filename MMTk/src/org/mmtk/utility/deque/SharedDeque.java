/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Lock;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

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
public class SharedDeque extends Deque 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  
  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   */
  SharedDeque(RawPageAllocator rpa, int arity) {
    this.rpa = rpa;
    this.arity = arity;
    lock = new Lock("SharedDeque");
    completionFlag = 0;
  }

  final boolean complete() {
    return completionFlag == 1;
  }

  final int getArity() throws VM_PragmaInline { return arity; }

  final void enqueue(VM_Address buf, int arity, boolean toTail) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(arity == this.arity);

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
    bufsenqueued++;
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(checkDequeLength(bufsenqueued));
    }
    unlock();
  }

  final void clearDeque(int arity) {
    VM_Address buf = dequeue(arity);
    while (!buf.isZero()) {
      free(bufferStart(buf));
      buf = dequeue(arity);
    }
  }

  final VM_Address dequeue(int arity) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(arity == this.arity);
    return dequeue(false);
  }

  final VM_Address dequeueAndWait(int arity) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(arity == this.arity);
    VM_Address buf = dequeue(false);
    while (buf.isZero() && (completionFlag == 0)) {
      buf = dequeue(true);
    }
    return buf;  
  }

  final void reset() {
    setNumClientsWaiting(0);
    setCompletionFlag(0);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(head.isZero() && tail.isZero());
  }

  final void newClient() {
    setNumClients(numClients + 1);
  }

  final VM_Address alloc() throws VM_PragmaInline {
    VM_Address rtn = rpa.alloc(PAGES_PER_BUFFER);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(rtn.EQ(bufferStart(rtn)));
    return rtn;
  }

  final void free(VM_Address buf) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(buf.EQ(bufferStart(buf)) && !buf.isZero());
    rpa.free(buf);
  }

  final int enqueuedPages() throws VM_PragmaInline {
    return bufsenqueued<<LOG_PAGES_PER_BUFFER;
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */
  private RawPageAllocator rpa;
  private int arity;
  private int completionFlag; //
  private int numClients; //
  private int numClientsWaiting; //
  private VM_Address head;
  private VM_Address tail;
  private int bufsenqueued;
  private Lock lock;

  
  private final VM_Address dequeue(boolean waiting) {
    lock();
    VM_Address rtn = VM_Address.zero();
    if (head.isZero()) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(tail.isZero());
      // no buffers available
      if (waiting) {
        setNumClientsWaiting(numClientsWaiting + 1);
        if (numClientsWaiting == numClients)
          setCompletionFlag(1);
      }
    } else {
      // dequeue the head buffer
      rtn = head;
      setHead(getNext(head));
      if (tail.EQ(rtn)) {
        setTail(VM_Address.zero());
        if (VM_Interface.VerifyAssertions) VM_Interface._assert(head.isZero());
      }
      bufsenqueued--;
      if (waiting)
        setNumClientsWaiting(numClientsWaiting - 1);
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
    VM_Magic.setMemoryAddress(buf, next);
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param bufRef The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  private final VM_Address getNext(VM_Address buf) {
    return VM_Magic.getMemoryAddress(buf);
  }

  /**
   * Check the number of buffers in the work queue (for debugging
   * purposes).
   *
   * @param length The number of buffers believed to be in the queue.
   * @return True if the length of the queue matches length.
   */
  private final boolean checkDequeLength(int length) {
    VM_Address top = head;
    int l = 0;
    while (!top.isZero() && l <= length) {
      top = getNext(top);
      l++;
    }
    return l == length;
  }

  /**
   * Lock this shared queue.  We use one simple low-level lock to
   * synchronize access to the shared queue of buffers.
   */
  private final void lock() {
    lock.acquire();
  }
  
  /**
   * Release the lock.  We use one simple low-level lock to synchronize
   * access to the shared queue of buffers.
   */
  private final void unlock() {
    lock.release();
  }

  // need to use this to avoid generating a putfield and so causing write barrier recursion
  //
  private final void setCompletionFlag(int flag) throws VM_PragmaInline {
    completionFlag = flag;
  }

  private final void setNumClients(int newNumClients) throws VM_PragmaInline {
      numClients = newNumClients;
  }

  private final void setNumClientsWaiting(int newNCW) throws VM_PragmaInline {
    numClientsWaiting = newNCW;
  }

  private final void setHead(VM_Address newHead) throws VM_PragmaInline {
    head = newHead;
  }

  private final void setTail(VM_Address newTail) throws VM_PragmaInline {
    tail = newTail;
  }
}
