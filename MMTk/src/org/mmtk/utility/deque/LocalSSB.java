/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

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
 * @version $Revision$
 * @date $Date$
 */ 
class LocalSSB extends Deque implements Constants, VM_Uninterruptible {
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
    tail = Deque.TAIL_INITIAL_VALUE;
  }

  /**
   * Flush the buffer to the shared queue (this will make any entries
   * in the buffer visible to any consumer associated with the shared
   * queue).
   */
  public void flushLocal() {
    if (tail.NE(Deque.TAIL_INITIAL_VALUE)) {
      closeAndEnqueueTail(queue.getArity());
      tail = Deque.TAIL_INITIAL_VALUE;
    }
  }
 
  /**
   * Reset the local buffer (throwing away any local entries).
   */
  void resetLocal() {
    tail = Deque.TAIL_INITIAL_VALUE;
  }

 /****************************************************************************
   *
   * Protected instance methods
   */

  /**
   * Check whether there is space in the buffer for a pending insert.
   * If there is not sufficient space, allocate a new buffer
   * (dispatching the full buffer to the shared queue if not null).
   *
   * @param arity The arity of the values stored in this SSB: the
   * buffer must contain enough space for this many words.
   */
  protected final void checkInsert(int arity) throws VM_PragmaInline {
    if (bufferOffset(tail).isZero())
      insertOverflow(arity);
    else if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bufferOffset(tail).sGE(VM_Word.fromIntZeroExtend(arity).lsh(LOG_BYTES_IN_ADDRESS).toOffset()));
  }

  /**
   * Insert a value into the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkInsert()</code> to ensure the
   * buffer can accommodate the insertion.
   *
   * @param value the value to be inserted.
   */
  protected final void uncheckedInsert(VM_Address value) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(bufferOffset(tail).sGE(VM_Offset.fromIntZeroExtend(BYTES_IN_ADDRESS)));
    tail = tail.sub(BYTES_IN_ADDRESS);
    VM_Magic.setMemoryAddress(tail, value);
    //    if (VM_Interface.VerifyAssertions) enqueued++;
  }

  /**
   * In the case where a tail buffer must be flushed before being
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
  protected final VM_Address normalizeTail(int arity) {
    VM_Address src = tail;
    VM_Address tgt = bufferFirst(tail);
    VM_Address last = tgt.add(bufferLastOffset(arity).sub(bufferOffset(tail)));
    while(tgt.LE(last)) {
      VM_Magic.setMemoryAddress(tgt, VM_Magic.getMemoryAddress(src));
      src = src.add(BYTES_IN_ADDRESS);
      tgt = tgt.add(BYTES_IN_ADDRESS);
    }
    return last;
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */
  protected VM_Address tail;   // the buffer
  protected SharedDeque queue; // the shared queue

  /**
   * Buffer space has been exhausted, allocate a new buffer and enqueue
   * the existing buffer (if any).
   *
   * @param arity The arity of this buffer (used for sanity test only).
   */
  private final void insertOverflow(int arity) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(arity == queue.getArity());
    if (tail.NE(Deque.TAIL_INITIAL_VALUE)) {
      closeAndEnqueueTail(arity);
    }
    tail = queue.alloc().add(bufferLastOffset(arity)).add(BYTES_IN_ADDRESS);
    Plan.checkForAsyncCollection(); // possible side-effect of alloc()
  }

  /**
   * Close the tail buffer (normalizing if necessary), and enqueue it
   * at the tail of the shared buffer queue.
   *
   *  @param arity The arity of this buffer.
   */
  private final void closeAndEnqueueTail(int arity) throws VM_PragmaNoInline {
    VM_Address last;
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
