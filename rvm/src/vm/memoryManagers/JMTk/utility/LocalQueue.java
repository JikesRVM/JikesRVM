/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;


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
public class LocalQueue extends LocalSSB 
  implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared queue to which this local queue will append
   * its buffers (when full or flushed).
   */
  LocalQueue(SharedQueue queue) {
    super(queue);
    head = headSentinel(queue.getArity());
  }

  /**
   * Flush the buffer to the shared queue (this will make any entries
   * in the buffer visible to any other consumer associated with the
   * shared queue).
   */
  public final void flushLocal() {
    super.flushLocal();
    if (head.NE(headSentinel(queue.getArity())))
      closeAndEnqueueHead(queue.getArity());
  }

  public final void reset() {
    head = headSentinel(queue.getArity());
  }

  /****************************************************************************
   *
   * Protected instance methods
   */

  /**
   * Check whether there is space in the buffer for a pending push.
   * If there is not sufficient space, allocate a new buffer
   * (dispatching the full buffer to the shared queue if not null).
   *
   * @param arity The arity of the values stored in this queue: the
   * buffer must contain enough space for this many words.
   */
  protected final void checkPush(int arity) throws VM_PragmaInline {
    if (VM_Address.fromInt(bufferOffset(head)).EQ(headSentinel(arity)))
      pushOverflow(arity);
    else if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bufferOffset(head) <= bufferLastOffset(arity));
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
    if ((bufferOffset(head) == 0) || (head.EQ(headSentinel(arity)))) {
      return popOverflow(arity);
    } else {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(bufferOffset(head) >= (arity<<LOG_BYTES_IN_ADDRESS));
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
  protected final void uncheckedPush(VM_Address value) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(bufferOffset(head) <= bufferLastOffset(queue.getArity()));
    VM_Magic.setMemoryAddress(head, value);
    head = head.add(BYTES_IN_ADDRESS);
    //    if (VM_Interface.VerifyAssertions) enqueued++;
  }

  /**
   * Pop a value from the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkPop()</code> to ensure the
   * buffer has sufficient values.
   *
   * @return the next int in the buffer
   */
  protected final int uncheckedPop() throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(bufferOffset(head) >= BYTES_IN_ADDRESS);
    head = head.sub(BYTES_IN_ADDRESS);
    // if (VM_Interface.VerifyAssertions) enqueued--;
    return VM_Magic.getMemoryInt(head);
  }

  /**
   * Pop a value from the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkPop()</code> to ensure the
   * buffer has sufficient values.
   *
   * @return the next address in the buffer
   */
  protected final VM_Address uncheckedPopAddress() throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bufferOffset(head) >= BYTES_IN_ADDRESS);
    head = head.sub(BYTES_IN_ADDRESS);
    // if (VM_Interface.VerifyAssertions) enqueued--;
    return VM_Magic.getMemoryAddress(head);
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */
  private VM_Address head;   // the head buffer

  /**
   * There is no space in the head buffer for a pending push. Allocate
   * a new buffer and enqueue the existing buffer (if any).
   *
   * @param arity The arity of this buffer (used for sanity test only).
   */
  private final void pushOverflow(int arity) throws VM_PragmaNoInline {
    if (head.NE(headSentinel(arity)))
      closeAndEnqueueHead(arity);
    head = queue.alloc();
    Plan.checkForAsyncCollection(); // possible side-effect of alloc()
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
    do {
      if (head.NE(headSentinel(arity)))
	queue.free(bufferStart(head));
      VM_Address tmp = queue.dequeue(arity);
      head = (tmp.isZero() ? headSentinel(arity) : tmp);
    } while (bufferOffset(head) == 0);

    if (head.EQ(headSentinel(arity)))
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
    head = headSentinel(queue.getArity());
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
    if (bufferOffset(tail) >= (arity<<LOG_BYTES_IN_ADDRESS)) {
      // entries in tail, so consume tail
      if (head.EQ(headSentinel(arity))) {
	head = queue.alloc(); // no head, so alloc a new one
	Plan.checkForAsyncCollection(); // possible side-effect of alloc()
      }
      VM_Address tmp = head;
      head = normalizeTail(arity).add(BYTES_IN_ADDRESS);// account for pre-decrement
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(tmp.EQ(bufferStart(tmp)));
      tail = tmp.add(bufferLastOffset(arity) + BYTES_IN_ADDRESS);
    } else {
      VM_Address tmp = queue.dequeueAndWait(arity);
      head = (tmp.isZero() ? headSentinel(arity) : tmp);
    }
    return head.NE(headSentinel(arity));
  }

  /**
   * Return the sentinel value used for testing whether a head buffer
   * is full.  This value is a funciton of the arity of the buffer.
   * 
   * @param arity The arity of this buffer  
   * @return The sentinel offset value for head buffers, used to test
   * whether a head buffer is full.
   */
  private final VM_Address headSentinel(int arity) throws VM_PragmaInline {
    return VM_Address.fromInt(bufferLastOffset(arity) + BYTES_IN_ADDRESS);
  }
}
