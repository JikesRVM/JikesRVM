/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.deque;

import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a local (<i>unsynchronized</i>) queue.
 * A queue is strictly FIFO.<p>
 *
 * Each instance stores word-sized values into a local buffer.  When
 * the buffer is full, or if the <code>flushLocal()</code> method is
 * called, the buffer enqueued at the tail of a
 * <code>SharedDeque</code>.
 *
 * The implementation is intended to be as efficient as possible, in
 * time and space, and is the basis for the <code>TraceBuffer</code> used by
 * heap trace generation. Each instance adds a single field to those inherited
 * from the SSB: a bump pointer.
 *
 * Preconditions: Buffers are always aligned on buffer-size address
 * boundaries.<p>
 *
 * Invariants: Buffers are filled such that tuples (of the specified
 * arity) are packed to the low end of the buffer.  Thus buffer
 * underflows will always arise when then cursor is buffer-size aligned.
 */
@Uninterruptible class LocalQueue extends LocalSSB implements Constants {

  /**
   * Constructor
   *
   * @param queue The shared queue to which this local ssb will append
   * its buffers (when full or flushed).
   */
  LocalQueue(SharedDeque queue) {
    super(queue);
  }

 /****************************************************************************
   *
   * Protected instance methods and fields
   */
  @Entrypoint
  protected Address head; // the start of the buffer

  /**
   * Reset the local buffer (throwing away any local entries).
   */
  public void resetLocal() {
    super.resetLocal();
    head = Deque.HEAD_INITIAL_VALUE;
  }

  /**
   * Check whether there are values in the buffer for a pending dequeue.
   * If there is not data, grab the first buffer on the shared queue
   * (after freeing the buffer).
   *
   * @param arity The arity of the values stored in this queue: the
   * buffer must contain enough space for this many words.
   */
  @Inline
  protected final boolean checkDequeue(int arity) {
    if (bufferOffset(head).isZero()) {
      return dequeueUnderflow(arity);
    } else {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(bufferOffset(head).sGE(Word.fromIntZeroExtend(arity).lsh(LOG_BYTES_IN_ADDRESS).toOffset()));
      return true;
    }
  }

  /**
   * Dequeue a value from the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkDequeue()</code> to ensure the
   * buffer has and entry to be removed.
   *
   * @return The first entry on the queue.
   */
  @Inline
  protected final Address uncheckedDequeue(){
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(bufferOffset(head).sGE(Offset.fromIntZeroExtend(BYTES_IN_ADDRESS)));
    head = head.minus(BYTES_IN_ADDRESS);
    return head.loadAddress();
  }

  /**
   * The head is empty (or null), and the shared queue has no buffers
   * available.  If the tail has sufficient entries, consume the tail.
   * Otherwise try wait on the global queue until either all other
   * clients of the queue reach exhaustion or a buffer becomes
   * available.
   *
   * @param arity The arity of this buffer
   * @return True if the consumer has eaten all the entries
   */
  protected final boolean headStarved(int arity) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == queue.getArity());

    // If the tail has entries...
    if (tail.NE(tailBufferEnd)) {
      head = normalizeTail(arity).plus(BYTES_IN_ADDRESS);
      tail = Deque.TAIL_INITIAL_VALUE;
      tailBufferEnd = Deque.TAIL_INITIAL_VALUE;
      // Return that we acquired more entries
      return false;
    }
    // Wait for another entry to materialize...
    head = queue.dequeueAndWait(arity);
    // return true if a) there is a head buffer, and b) it is non-empty
    return (head.EQ(Deque.HEAD_INITIAL_VALUE) || bufferOffset(head).isZero());
  }

  /****************************************************************************
   *
   * Private instance methods
   */

  /**
   * There are not sufficient entries in the head buffer for a pending
   * dequeue.  Acquire a new head buffer.  If the shared queue has no
   * buffers available, consume the tail if necessary.  Return false
   * if entries cannot be acquired.
   *
   * @param arity The arity of this buffer (used for sanity test only).
   * @return True if there the head buffer has been successfully
   * replenished.
   */
  @NoInline
  private boolean dequeueUnderflow(int arity) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(arity == queue.getArity());
    do {
      if (head.NE(Deque.HEAD_INITIAL_VALUE))
        queue.free(head);
      head = queue.dequeue(arity);
    } while (head.NE(Deque.HEAD_INITIAL_VALUE) && bufferOffset(head).isZero());

    if (head.EQ(Deque.HEAD_INITIAL_VALUE))
      return !headStarved(arity);

    return true;
  }
}
