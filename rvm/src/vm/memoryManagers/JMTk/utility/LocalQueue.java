/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * Note this may perform poorly when used as simple (concurrent) FIFO,
 * with interleaved insert and pop operations, in the case where the
 * local buffer is nearly empty and more pops than inserts are
 * performed.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class LocalQueue extends LocalSSB implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //

  /**
   * Constructor
   *
   * @param queue The shared queue to which this local queue will append
   * its buffers (when full or flushed).
   */
  LocalQueue(SharedQueue queue) {
    super(queue);
    head = VM_Address.fromInt(headSentinel(queue.getArity()));
  }

  /**
   * Flush the buffer to the shared queue (this will make any entries
   * in the buffer visible to any other consumer associated with the
   * shared queue).
   */
  public final void flushLocal() {
    super.flushLocal();
    if (head.NE(VM_Address.fromInt(headSentinel(queue.getArity())))) {
      closeAndEnqueueHead(queue.getArity());
      head = VM_Address.fromInt(headSentinel(queue.getArity()));
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Protected instance methods
  //

  /**
   * Check whether there is space in the buffer for a pending push.
   * If there is not sufficient space, allocate a new buffer
   * (dispatching the full buffer to the shared queue if not null).
   *
   * @param arity The arity of the values stored in this queue: the
   * buffer must contain enough space for this many words.
   */
  protected final void checkPush(int arity) throws VM_PragmaInline {
    if (bufferOffset(head) == headSentinel(arity))
      pushOverflow(arity);
    else if (VM.VerifyAssertions)
      VM._assert(bufferOffset(head) <= bufferLastOffset(arity));
  }
  
  /**
   * Check whether there are sufficient entries in the head buffer for
   * a pending pop.  If there are not sufficient entries, acquire a
   * new buffer from the shared queeue. Return true if there are
   * enough entries for the pending pop, false if the queue has been
   * exhausted.
   *
   * @param arity The arity of the values stored in this queue: there
   * must be at least this many values available.
   * @return true if there are enough entries for the pending pop,
   * false if the queue has been exhausted.
   */
  protected final boolean checkPop(int arity) throws VM_PragmaInline {
    if (bufferOffset(head) == 0)
      return popOverflow(arity);
    else {
      if (VM.VerifyAssertions)
	VM._assert(bufferOffset(head) > (arity<<LOG_WORD_SIZE));
      return true;
    }
  }

  /**
   * Push a value onto the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkPush()</code> to ensure the
   * buffer can accommodate the insertion.
   *
   * @param value the value to be inserted.
   */
  protected final void uncheckedPush(int value) throws VM_PragmaInline {
    if (VM.VerifyAssertions) 
      VM._assert(bufferOffset(head) <= bufferLastOffset(queue.getArity()));
    VM_Magic.setMemoryWord(head, value);
    head = head.add(WORD_SIZE);
    //    if (VM.VerifyAssertions) enqueued++;
  }

  /**
   * Pop a value from the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkPop()</code> to ensure the
   * buffer has sufficient values.
   *
   * @return the next value in the buffer
   */
  protected final int uncheckedPop() throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bufferOffset(head) >= WORD_SIZE);
    head = head.sub(WORD_SIZE);
    //    if (VM.VerifyAssertions) enqueued--;
    return VM_Magic.getMemoryWord(head);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private instance methods and fields
  //
  private VM_Address head;   // the head buffer

  /**
   * There is no space in the head buffer for a pending push. Allocate
   * a new buffer and enqueue the existing buffer (if any).
   *
   * @param arity The arity of this buffer (used for sanity test only).
   */
  private final void pushOverflow(int arity) throws VM_PragmaNoInline {
    if (head.NE(VM_Address.fromInt(headSentinel(arity)))) {
      closeAndEnqueueHead(arity);
    }
    head = queue.alloc();
  }

  /**
   * There are not sufficient entries in the head buffer for a pending
   * pop.  Acquire a new head buffer.  If the shared queue has no
   * buffers available, consume the tail if necessary.  Return false
   * if entries cannot be acquired.
   *
   * @param arity The arity of this buffer (used for sanity test only).
   * @return True if there the head buffer has been successfully
   * replenished.
   */
  private final boolean popOverflow(int arity) throws VM_PragmaNoInline {
    if (head.NE(VM_Address.fromInt(headSentinel(arity)))) {
      queue.free(bufferStart(head));
    }
    head = queue.dequeue(arity);
    if (head.isZero())
      return consumerStarved(arity);
    else 
      return true;
  }

  /**
   * Close the head buffer and enqueue it in the shared buffer queue.
   *
   * @param arity The arity of this buffer (used for sanity test only).
   */
  private final void closeAndEnqueueHead(int arity) throws VM_PragmaNoInline {
    queue.enqueue(head, arity, false);
  }

  /**
   * The head is empty (or null), and the shared queue has no buffers
   * available.  If the tail has sufficient entries, consume the tail.
   * Otherwise try wait on the global queue until either all other
   * clients of the queue reach exhaustion or a buffer becomes
   * available.
   *
   * @param arity The arity of this buffer  
   * @return True if more entires were aquired.
   */
  private final boolean consumerStarved(int arity) {
    if (bufferOffset(tail) >= (arity<<LOG_WORD_SIZE)) {
      // entries in tail, so consume tail
      if (head.EQ(VM_Address.fromInt(headSentinel(arity))))
	head = queue.alloc(); // no head, so alloc a new one
      VM_Address tmp = head;
      head = normalizeTail(arity).add(WORD_SIZE); // account for pre-decrement
      if (VM.VerifyAssertions) VM._assert(tmp.EQ(bufferStart(tmp)));
      tail = tmp.add(bufferLastOffset(arity) + WORD_SIZE);
    } else
      head = queue.dequeueAndWait(arity);
    return !(head.isZero());
  }

  /**
   * Return the sentinel value used for testing whether a head buffer
   * is full.  This value is a funciton of the arity of the buffer.
   * 
   * @param arity The arity of this buffer  
   * @return The sentinel offset value for head buffers, used to test
   * whether a head buffer is full.
   */
  private final int headSentinel(int arity) throws VM_PragmaInline {
    return bufferLastOffset(arity) + WORD_SIZE;
  }
}
