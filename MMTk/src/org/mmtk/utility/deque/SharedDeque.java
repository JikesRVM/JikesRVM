/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.deque;

import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;

import org.mmtk.vm.Lock;
import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of buffers
 * for shared use.  The data can be added to and removed from either end
 * of the deque.
 */
@Uninterruptible public class SharedDeque extends Deque implements Constants {

  private static final Offset PREV_OFFSET = Offset.fromIntSignExtend(BYTES_IN_ADDRESS);

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
 */
  public SharedDeque(RawPageSpace rps, int arity) {
    this.rps = rps;
    this.arity = arity;
    lock = VM.newLock("SharedDeque");
    completionFlag = 0;
    head = HEAD_INITIAL_VALUE;
    tail = TAIL_INITIAL_VALUE;
  }

  final boolean complete() {
    return completionFlag == 1;
  }

  @Inline
  final int getArity() { return arity; }

  final void enqueue(Address buf, int arity, boolean toTail) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == this.arity);
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
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(checkDequeLength(bufsenqueued));
    unlock();
  }

  public final void clearDeque(int arity) {
    Address buf = dequeue(arity);
    while (!buf.isZero()) {
      free(bufferStart(buf));
      buf = dequeue(arity);
    }
  }

  @Inline
  final Address dequeue(int arity) {
    return dequeue(arity, false);
  }

  final Address dequeue(int arity, boolean fromTail) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == this.arity);
    return dequeue(false, fromTail);
  }

  @Inline
  final Address dequeueAndWait(int arity) {
    return dequeueAndWait(arity, false);
  }

  final Address dequeueAndWait(int arity, boolean fromTail) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == this.arity);
    Address buf = dequeue(false, fromTail);
    while (buf.isZero() && (completionFlag == 0)) {
      buf = dequeue(true, fromTail);
    }
    return buf;
  }

  public final void reset() {
    setNumConsumersWaiting(0);
    setCompletionFlag(0);
    assertExhausted();
  }

  public final void assertExhausted() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(head.isZero() && tail.isZero());
  }

  public final void newConsumer() {
    setNumConsumers(numConsumers + 1);
  }

  @Inline
  final Address alloc() {
    Address rtn = rps.acquire(PAGES_PER_BUFFER);
    if (rtn.isZero()) {
      Space.printUsageMB();
      VM.assertions.fail("Failed to allocate space for queue.  Is metadata virtual memory exhausted?");
    }
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn.EQ(bufferStart(rtn)));
    return rtn;
  }

  @Inline
  final void free(Address buf) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(buf.EQ(bufferStart(buf)) && !buf.isZero());
    rps.release(buf);
  }

  @Inline
  public final int enqueuedPages() {
    return bufsenqueued << LOG_PAGES_PER_BUFFER;
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */
  private RawPageSpace rps;
  private int arity;
  @Entrypoint
  private int completionFlag; //
  @Entrypoint
  private int numConsumers; //
  @Entrypoint
  private int numConsumersWaiting; //
  @Entrypoint
  protected Address head;
  @Entrypoint
  protected Address tail;
  @Entrypoint
  private int bufsenqueued;
  private Lock lock;


  private Address dequeue(boolean waiting, boolean fromTail) {
    lock();
    Address rtn = ((fromTail) ? tail : head);
    if (rtn.isZero()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tail.isZero() && head.isZero());
      // no buffers available
      if (waiting) {
        setNumConsumersWaiting(numConsumersWaiting + 1);
        if (numConsumersWaiting == numConsumers)
          setCompletionFlag(1);
      }
    } else {
      if (fromTail) {
        // dequeue the tail buffer
        setTail(getPrev(tail));
        if (head.EQ(rtn)) {
          setHead(Address.zero());
          if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tail.isZero());
        } else {
          setNext(tail, Address.zero());
        }
      } else {
        // dequeue the head buffer
        setHead(getNext(head));
        if (tail.EQ(rtn)) {
          setTail(Address.zero());
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(head.isZero());
        } else {
          setPrev(head, Address.zero());
        }
      }
      bufsenqueued--;
      if (waiting)
        setNumConsumersWaiting(numConsumersWaiting - 1);
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
  private static void setNext(Address buf, Address next) {
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
  private void setPrev(Address buf, Address prev) {
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
  private boolean checkDequeLength(int length) {
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
  private void lock() {
    lock.acquire();
  }

  /**
   * Release the lock.  We use one simple low-level lock to synchronize
   * access to the shared queue of buffers.
   */
  private void unlock() {
    lock.release();
  }

  // need to use this to avoid generating a putfield and so causing write barrier recursion
  //
  @Inline
  private void setCompletionFlag(int flag) {
    completionFlag = flag;
  }

  @Inline
  private void setNumConsumers(int newNumConsumers) {
    numConsumers = newNumConsumers;
  }

  @Inline
  private void setNumConsumersWaiting(int newNCW) {
    numConsumersWaiting = newNCW;
  }

  @Inline
  private void setHead(Address newHead) {
    head = newHead;
  }

  @Inline
  private void setTail(Address newTail) {
    tail = newTail;
  }
}
