/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility;

import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_AddressArray;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of buffers
 * for shared use.  The data can be added to and removed from either end
 * of the deque.  
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class SharedDeque extends Deque implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   */
  public SharedDeque(RawPageAllocator rpa, int arity) {
    this.rpa = rpa;
    this.arity = arity;
    lock = new Lock("SharedDeque");
    completionFlag = 0;
    head = HEAD_INITIAL_VALUE;
    tail = TAIL_INITIAL_VALUE;
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
      if (tail.EQ(TAIL_INITIAL_VALUE))
        head = buf;
      else
        setNext(tail, buf);
      setPrev(buf, tail);
      tail = buf;
    } else {
      // Add to the head of the queue
      setPrev(buf, VM_Address.zero());
      if (head.EQ(HEAD_INITIAL_VALUE))
        tail = buf;
      else
	setPrev(head, buf);
      setNext(buf, head);
      head = buf;
    } 
    bufsenqueued++;
    if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(checkDequeLength(bufsenqueued));
    }
    unlock();
  }

  public final void clearDeque(int arity) {
    VM_Address buf = dequeue(arity);
    while (!buf.isZero()) {
      free(bufferStart(buf));
      buf = dequeue(arity);
    }
  }

  final VM_Address dequeue(int arity) throws VM_PragmaInline {
    return dequeue(arity, false);
  }

  final VM_Address dequeue(int arity, boolean fromTail) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(arity == this.arity);
    return dequeue(false, fromTail);
  }

  final VM_Address dequeueAndWait(int arity) throws VM_PragmaInline {
    return dequeueAndWait(arity, false);
  }

  final VM_Address dequeueAndWait(int arity, boolean fromTail) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(arity == this.arity);
    VM_Address buf = dequeue(false, fromTail);
    while (buf.isZero() && (completionFlag == 0)) {
      buf = dequeue(true, fromTail);
    }
    return buf;  
  }

  public final void reset() {
    setNumClientsWaiting(0);
    setCompletionFlag(0);
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(head.isZero() && tail.isZero());
  }

  public final void newClient() {
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
  protected VM_Address head;
  protected VM_Address tail;
  private int bufsenqueued;
  private Lock lock;

  
  private final VM_Address dequeue(boolean waiting, boolean fromTail) {
    lock();
    VM_Address rtn = ((fromTail) ? tail : head);
    if (rtn.isZero()) {
      if (VM_Interface.VerifyAssertions) 
	VM_Interface._assert(tail.isZero() && head.isZero());
      // no buffers available
      if (waiting) {
        setNumClientsWaiting(numClientsWaiting + 1);
        if (numClientsWaiting == numClients)
          setCompletionFlag(1);
      }
    } else {
      if (fromTail) {
	// dequeue the tail buffer
	setTail(getPrev(tail));	
	if (head.EQ(rtn)) {
	  setHead(VM_Address.zero());
	  if (VM_Interface.VerifyAssertions) VM_Interface._assert(tail.isZero());
	} else {
	  setNext(tail, VM_Address.zero());
	}
      } else {
      // dequeue the head buffer
      setHead(getNext(head));
      if (tail.EQ(rtn)) {
        setTail(VM_Address.zero());
        if (VM_Interface.VerifyAssertions) VM_Interface._assert(head.isZero());
	} else {
	  setPrev(head, VM_Address.zero());
	}
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
  protected final VM_Address getNext(VM_Address buf) {
    return VM_Magic.getMemoryAddress(buf);
  }

  /**
   * Set the "prev" pointer in a buffer forming the linked buffer chain.
   *
   * @param bufRef The buffer whose next field is to be set.
   * @param next The reference to which next should point.
   */
  private final void setPrev(VM_Address buf, VM_Address prev) {
    VM_Magic.setMemoryAddress(buf.add(BYTES_IN_ADDRESS), prev);
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param bufRef The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  protected final VM_Address getPrev(VM_Address buf) {
    return VM_Magic.getMemoryAddress(buf.add(BYTES_IN_ADDRESS));
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
