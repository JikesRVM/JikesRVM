/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.vm.Plan;
import org.mmtk.vm.Constants;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements a local (<i>unsynchronized</i>) sequential
 * store buffer.  An SSB is strictly FIFO (although this class does
 * not implement dequeuing).<p>
 *
 * Each instance stores word-sized values into a local buffer.  When
 * the buffer is full, or if the <code>flushLocal()</code> method is
 * called, the buffer enqueued at the tail of a
 * <code>SharedDeque</code>.  This class provides no mechanism for
 * dequeing.<p>
 *
 * The implementation is intended to be as efficient as possible, in
 * time and space, as it is used in critical code such as the GC work
 * queue and the write buffer used by many "remembering"
 * collectors. Each instance has just two fields: a bump pointer and a
 * pointer to the <code>SharedDeque</code><p>
 *
 * Preconditions: Buffers are always aligned on buffer-size address
 * boundaries.<p>
 *
 * Invariants: Buffers are filled such that tuples (of the specified
 * arity) are packed to the low end of the buffer.  Thus buffer
 * overflows on inserts and pops (underflow actually) will always arise
 * when then cursor is buffer-size aligned.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
class LocalSSB extends Deque implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared queue to which this local ssb will append
   * its buffers (when full or flushed).
   */
  LocalSSB(SharedDeque queue) {
    this.queue = queue;
    resetLocal();
  }

  /**
   * Flush the buffer and add it to the shared queue (this will
   * make any entries in the buffer visible to any consumer associated
   * with the shared queue).
   */
  public void flushLocal() {
    if (tail.NE(Deque.TAIL_INITIAL_VALUE)) {
      closeAndEnqueueTail(queue.getArity());
      tail = Deque.TAIL_INITIAL_VALUE;
      tailBufferEnd = Deque.TAIL_INITIAL_VALUE;
    }
  }
 
  public void reset() {
    resetLocal();
  }

 /****************************************************************************
   *
   * Protected instance methods and fields
   */
  protected Address tail;            // the location in the buffer
  protected Address tailBufferEnd;   // the end of the buffer
  protected SharedDeque queue;          // the shared queue

  /**
   * Reset the local buffer (throwing away any local entries).
   */
  public void resetLocal() {
    tail = Deque.TAIL_INITIAL_VALUE;
    tailBufferEnd = Deque.TAIL_INITIAL_VALUE;
  }

  /**
   * Check whether there is space in the buffer for a pending insert.
   * If there is not sufficient space, allocate a new buffer
   * (dispatching the full buffer to the shared queue if not null).
   *
   * @param arity The arity of the values stored in this SSB: the
   * buffer must contain enough space for this many words.
   */
  protected final void checkTailInsert(int arity) throws InlinePragma {
    if (bufferOffset(tail).isZero())
      tailOverflow(arity);
    else Assert._assert(bufferOffset(tail).sGE(Word.fromIntZeroExtend(arity).lsh(LOG_BYTES_IN_ADDRESS).toOffset()));
  }

  /**
   * Insert a value into the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkInsert()</code> to ensure the
   * buffer can accommodate the insertion.
   *
   * @param value the value to be inserted.
   */
  protected final void uncheckedTailInsert(Address value) throws InlinePragma {
    Assert._assert(bufferOffset(tail).sGE(Offset.fromIntZeroExtend(BYTES_IN_ADDRESS)));
    tail = tail.sub(BYTES_IN_ADDRESS);
    tail.store(value);
    //    if (VM_Interface.VerifyAssertions) enqueued++;
  }

  /**
   * In the case where a buffer must be flushed before being
   * filled (either to the queue or to the head), the entries must be
   * slid to the base of the buffer in order to preserve the invariant
   * that all non-tail buffers will have entries starting at the base
   * (which allows a simple test against the base to be used when
   * popping entries).  This is <i>expensive</i>, so should be
   * avoided.
   * 
   * @param arity The arity of the buffer in question
   * @return The last slot in the normalized buffer that contains an entry
   */
  protected final Address normalizeTail(int arity) {
    Address src = tail;
    Address tgt = bufferFirst(tail);
    Address last = tgt.add(bufferLastOffset(arity).sub(bufferOffset(tail)));
    while(tgt.LE(last)) {
      tgt.store(src.loadAddress());
      src = src.add(BYTES_IN_ADDRESS);
      tgt = tgt.add(BYTES_IN_ADDRESS);
    }
    return last;
  }

  /**
   * Return the sentinel offset for a buffer of a given arity.  This is used
   * both to compute the address at the end of the buffer.
   *
   * @param arity The arity of this buffer
   * @return The sentinel offset value for a buffer of the given arity.
   */
  protected final Offset bufferSentinel(int arity) throws InlinePragma {
    return bufferLastOffset(arity).add(BYTES_IN_ADDRESS);
  }

  /****************************************************************************
   *
   * Private instance methods
   */

  /**
   * Buffer space has been exhausted, allocate a new buffer and enqueue
   * the existing buffer (if any).
   *
   * @param arity The arity of this buffer (used for sanity test only).
   */
  private final void tailOverflow(int arity) {
    Assert._assert(arity == queue.getArity());
    if (tail.NE(Deque.TAIL_INITIAL_VALUE)) {
      closeAndEnqueueTail(arity);
    }
    tail = queue.alloc().add(bufferSentinel(arity));
    tailBufferEnd = tail;
    Plan.checkForAsyncCollection(); // possible side-effect of alloc()
  }

  /**
   * Close the tail buffer (normalizing if necessary), and enqueue it
   * at the tail of the shared buffer queue.
   *
   *  @param arity The arity of this buffer.
   */
  private final void closeAndEnqueueTail(int arity) throws NoInlinePragma {
    Address last;
    if (!bufferOffset(tail).isZero()) {
      // prematurely closed
      last = normalizeTail(arity);
    } else {
      // a full tail buffer
      last = tail.add(bufferLastOffset(arity));
    }
    queue.enqueue(last.add(BYTES_IN_ADDRESS), arity, true);
  }
}
