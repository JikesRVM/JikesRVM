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
 * Note this may perform poorly when used as simple (concurrent) FIFO,
 * with interleaved insert and pop operations, in the case where the
 * local buffer is nearly empty and more pops than inserts are
 * performed.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$

 */ 
public class LocalDeque extends LocalSSB 
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
  LocalDeque(SharedDeque queue) {
    super(queue);
    reset();
  }

  /**
   * Flush the buffer to the shared queue (this will make any entries
   * in the buffer visible to any other consumer associated with the
   * shared queue).
   */
  public final void flushLocal() {
    super.flushLocal();
    if (!isReset()) 
      closeAndEnqueueHead(queue.getArity());
  }

  /**
   * Reset the local buffer (throwing away any local entries).
   */
  void resetLocal() {
    super.flushLocal();
    reset();
  }

  protected final void reset() {
    head = VM_Address.zero().add(headSentinel(queue.getArity()));
  }

  public final boolean isReset() throws VM_PragmaInline {
    return head.EQ(VM_Address.zero().add(headSentinel(queue.getArity())));
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
    if (bufferOffset(head).EQ(headSentinel(arity)))
      pushOverflow(arity);
    else if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bufferOffset(head).sLE(bufferLastOffset(arity)));
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
    if ((bufferOffset(head).isZero()) || isReset()) {
      return popOverflow(arity);
    } else {
      if (VM_Interface.VerifyAssertions)
        VM_Interface._assert(bufferOffset(head).sGE(VM_Word.fromIntZeroExtend(arity).lsh(LOG_BYTES_IN_ADDRESS).toOffset()));
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
      VM_Interface._assert(bufferOffset(head).sLE(bufferLastOffset(queue.getArity())));
    VM_Magic.setMemoryAddress(head, value);
    head = head.add(BYTES_IN_ADDRESS);
    //    if (VM_Interface.VerifyAssertions) enqueued++;
  }

  /**
   * Pop an address value from the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkPop()</code> to ensure the
   * buffer has sufficient values.
   *
   * @return the next int in the buffer
   */
  protected final VM_Address uncheckedPop() throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(bufferOffset(head).sGE(VM_Offset.fromIntZeroExtend(BYTES_IN_ADDRESS)));
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
    if (!isReset())
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(arity == queue.getArity());
    VM_Address sentinelAsAddress = VM_Address.zero().add(headSentinel(arity));
    do {
      if (!isReset())
        queue.free(bufferStart(head));
      VM_Address tmp = queue.dequeue(arity);
      head = tmp.isZero() ? sentinelAsAddress : tmp;
    } while (bufferOffset(head).isZero());

    if (head.EQ(sentinelAsAddress))
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
    reset();
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(arity == queue.getArity());
    VM_Address sentinelAsAddress = VM_Address.zero().add(headSentinel(arity)); 
    if (bufferOffset(tail).sGE(VM_Word.fromIntZeroExtend(arity).lsh(LOG_BYTES_IN_ADDRESS).toOffset())) {
      // entries in tail, so consume tail
      if (isReset()) {
        head = queue.alloc(); // no head, so alloc a new one
        Plan.checkForAsyncCollection(); // possible side-effect of alloc()
      }
      VM_Address tmp = head;
      head = normalizeTail(arity).add(BYTES_IN_ADDRESS);// account for pre-decrement
      if (VM_Interface.VerifyAssertions)
        VM_Interface._assert(tmp.EQ(bufferStart(tmp)));
      tail = tmp.add(bufferLastOffset(arity)).add(BYTES_IN_ADDRESS);
    } else {
      VM_Address tmp = queue.dequeueAndWait(arity);
      head = (tmp.isZero() ? sentinelAsAddress : tmp);
    }
    // return true if a) there is a head buffer, and b) it is non-empty
    return ((!isReset()) && (!(bufferOffset(head).isZero())));
  }

  /**
   * Return the sentinel value used for testing whether a head buffer
   * is full.  This value is a funciton of the arity of the buffer.
   * 
   * @param arity The arity of this buffer  
   * @return The sentinel offset value for head buffers, used to test
   * whether a head buffer is full.
   */
  private final VM_Offset headSentinel(int arity) throws VM_PragmaInline {
    return bufferLastOffset(arity).add(BYTES_IN_ADDRESS);
  }
}
