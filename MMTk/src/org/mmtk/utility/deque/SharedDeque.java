/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Lock;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
public class SharedDeque extends Deque implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  private static final Offset PREV_OFFSET = Offset.fromInt(BYTES_IN_ADDRESS);

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   */
  public SharedDeque(RawPageSpace rps, int arity) {
    this.rps = rps;
    this.arity = arity;
    lock = new Lock("SharedDeque");
    completionFlag = 0;
    head = HEAD_INITIAL_VALUE;
    tail = TAIL_INITIAL_VALUE;
  }

  final boolean complete() {
    return completionFlag == 1;
  }

  final int getArity() throws InlinePragma { return arity; }

  final void enqueue(Address buf, int arity, boolean toTail) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(arity == this.arity);
    lock();
    if (toTail) {
      // Add to the tail of the queue
      setNext(buf, Address.zero());
      if (tail.EQ(TAIL_INITIAL_VALUE))
        head = buf;
      else
        setNext(tail, buf);
      setPrev(buf, tail);
      tail = buf;
    } else {
      // Add to the head of the queue
      setPrev(buf, Address.zero());
      if (head.EQ(HEAD_INITIAL_VALUE))
        tail = buf;
      else
	setPrev(head, buf);
      setNext(buf, head);
      head = buf;
    } 
    bufsenqueued++;
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(checkDequeLength(bufsenqueued));
    unlock();
  }

  public final void clearDeque(int arity) {
    Address buf = dequeue(arity);
    while (!buf.isZero()) {
      free(bufferStart(buf));
      buf = dequeue(arity);
    }
  }

  final Address dequeue(int arity) throws InlinePragma {
    return dequeue(arity, false);
  }

  final Address dequeue(int arity, boolean fromTail) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(arity == this.arity);
    return dequeue(false, fromTail);
  }

  final Address dequeueAndWait(int arity) throws InlinePragma {
    return dequeueAndWait(arity, false);
  }

  final Address dequeueAndWait(int arity, boolean fromTail) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(arity == this.arity);
    Address buf = dequeue(false, fromTail);
    while (buf.isZero() && (completionFlag == 0)) {
      buf = dequeue(true, fromTail);
    }
    return buf;  
  }

  public final void reset() {
    setNumClientsWaiting(0);
    setCompletionFlag(0);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(head.isZero() && tail.isZero());
  }

  public final void newClient() {
    setNumClients(numClients + 1);
  }

  final Address alloc() throws InlinePragma {
    Address rtn = rps.acquire(PAGES_PER_BUFFER);
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(rtn.EQ(bufferStart(rtn)));
    return rtn;
  }

  final void free(Address buf) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(buf.EQ(bufferStart(buf)) && !buf.isZero());
    rps.release(buf);
  }

  public final int enqueuedPages() throws InlinePragma {
    return bufsenqueued<<LOG_PAGES_PER_BUFFER;
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */
  private RawPageSpace rps;
  private int arity;
  private int completionFlag; //
  private int numClients; //
  private int numClientsWaiting; //
  protected Address head;
  protected Address tail;
  private int bufsenqueued;
  private Lock lock;

  
  private final Address dequeue(boolean waiting, boolean fromTail) {
    lock();
    Address rtn = ((fromTail) ? tail : head);
    if (rtn.isZero()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(tail.isZero() && head.isZero());
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
	  setHead(Address.zero());
	  if (Assert.VERIFY_ASSERTIONS) Assert._assert(tail.isZero());
	} else {
	  setNext(tail, Address.zero());
	}
      } else {
      // dequeue the head buffer
      setHead(getNext(head));
      if (tail.EQ(rtn)) {
        setTail(Address.zero());
        if (Assert.VERIFY_ASSERTIONS) Assert._assert(head.isZero());
	} else {
	  setPrev(head, Address.zero());
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
   * @param buf The buffer whose next field is to be set.
   * @param next The reference to which next should point.
   */
  private static final void setNext(Address buf, Address next) {
    buf.store(next);
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  protected final Address getNext(Address buf) {
    return buf.loadAddress();
  }

  /**
   * Set the "prev" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be set.
   * @param prev The reference to which prev should point.
   */
  private final void setPrev(Address buf, Address prev) {
    buf.store(prev, PREV_OFFSET);
  }

  /**
   * Get the "next" pointer in a buffer forming the linked buffer chain.
   *
   * @param buf The buffer whose next field is to be returned.
   * @return The next field for this buffer.
   */
  protected final Address getPrev(Address buf) {
    return buf.loadAddress(PREV_OFFSET);
  }

  /**
   * Check the number of buffers in the work queue (for debugging
   * purposes).
   *
   * @param length The number of buffers believed to be in the queue.
   * @return True if the length of the queue matches length.
   */
  private final boolean checkDequeLength(int length) {
    Address top = head;
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
  private final void setCompletionFlag(int flag) throws InlinePragma {
    completionFlag = flag;
  }

  private final void setNumClients(int newNumClients) throws InlinePragma {
      numClients = newNumClients;
  }

  private final void setNumClientsWaiting(int newNCW) throws InlinePragma {
    numClientsWaiting = newNCW;
  }

  private final void setHead(Address newHead) throws InlinePragma {
    head = newHead;
  }

  private final void setTail(Address newTail) throws InlinePragma {
    tail = newTail;
  }
}
