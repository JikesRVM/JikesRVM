/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility;

import org.mmtk.plan.Plan;
import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * Note this may perform poorly when being used as a FIFO structure with
 * insertHead and pop operations operating on the same buffer.  This
 * only uses the fields inherited from <code>LocalQueue</code>, but adds
 * the ability for entries to be added to the head of the deque and popped
 * from the rear.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class LocalDeque extends LocalQueue 
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared deque to which this local deque will append
   * its buffers (when full or flushed).
   */
  LocalDeque(SharedDeque queue) {
    super(queue);
  }

  /**
   * Flush the buffer to the shared deque (this will make any entries
   * in the buffer visible to any other consumer associated with the
   * shared deque).
   */
  public final void flushLocal() {
    super.flushLocal();
    if (head.NE(Deque.HEAD_INITIAL_VALUE)) {
      closeAndInsertHead(queue.getArity());
      head = Deque.HEAD_INITIAL_VALUE;
  }
  }

  /****************************************************************************
   *
   * Protected instance methods
   */

  /**
   * Check whether there is space in the buffer for a pending insert.
   * If there is not sufficient space, allocate a new buffer
   * (dispatching the full buffer to the shared deque if not null).
   *
   * @param arity The arity of the values stored in this deque: the
   * buffer must contain enough space for this many words.
   */
  protected final void checkHeadInsert(int arity) throws VM_PragmaInline {
    if (bufferOffset(head).EQ(bufferSentinel(arity)) || 
	head.EQ(HEAD_INITIAL_VALUE))
      headOverflow(arity);
    else if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bufferOffset(head).sLE(bufferLastOffset(arity)));
  }
  
  /**
   * Insert a value at the front of the deque (and buffer).  This is 
   * <i>unchecked</i>.  The caller must first call 
   * <code>checkHeadInsert()</code> to ensure the buffer can accommodate 
   * the insertion.
   *
   * @param value the value to be inserted.
   */
  protected final void uncheckedHeadInsert(VM_Address value) 
    throws VM_PragmaInline {
      if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(bufferOffset(head).sLT(bufferSentinel(queue.getArity())));
    VM_Magic.setMemoryAddress(head, value);
    head = head.add(BYTES_IN_ADDRESS);
    //    if (VM_Interface.VerifyAssertions) enqueued++;
  }

  /**
   * Check whether there are sufficient entries in the tail buffer for
   * a pending pop.  If there are not sufficient entries, acquire a
   * new buffer from the shared deque. Return true if there are
   * enough entries for the pending pop, false if the deque has been
   * exhausted.
   *
   * @param arity The arity of the values stored in this deque: there
   * must be at least this many values available.
   * @return True if there are enough entries for the pending pop,
   * false if the queue has been exhausted.
   */
  protected final boolean checkPop(int arity) throws VM_PragmaInline {
    if (tail.EQ(tailBufferEnd)) {
      return popUnderflow(arity);
    } else if (VM_Interface.VerifyAssertions) {
      VM_Interface._assert(bufferOffset(tail).sLT(bufferSentinel(queue.getArity())));
    }
    return true;
  }

  /**
   * Pop an address value from the buffer.  This is <i>unchecked</i>.  The
   * caller must first call <code>checkPop()</code> to ensure the
   * buffer has sufficient values.
   *
   * @return the next address in the buffer
   */
  protected final VM_Address uncheckedPop() throws VM_PragmaInline {
    VM_Address retVal;
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(tail.LT(tailBufferEnd));
    // if (VM_Interface.VerifyAssertions) enqueued--;
    retVal = VM_Magic.getMemoryAddress(tail);
    tail = tail.add(BYTES_IN_ADDRESS);
    return retVal;
  }

  /****************************************************************************
   *
   * Private instance methods and fields
   */

  /**
   * Buffer space has been exhausted, allocate a new buffer and enqueue
   * the existing buffer (if any).
   *
   * @param arity The arity of this buffer (used for sanity test only).
   */
  private final void headOverflow(int arity) {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(arity == queue.getArity());
    if (head.NE(Deque.HEAD_INITIAL_VALUE))
      closeAndInsertHead(arity);

    head = queue.alloc();
    Plan.checkForAsyncCollection(); // possible side-effect of alloc()
  }

  /**
   * There are not sufficient entries in the head buffer for a pending
   * pop.  Acquire a new tail buffer.  If the shared deque has no
   * buffers available, consume the head if necessary.  Return false
   * if entries cannot be acquired.
   *
   * @param arity The arity of this buffer (used for sanity test only).
   * @return True if the buffer has been successfully
   * replenished.
   */
  private final boolean popUnderflow(int arity) throws VM_PragmaNoInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(arity == queue.getArity());
    do {
      if (tail.NE(Deque.TAIL_INITIAL_VALUE))
	queue.free(head);
      tailBufferEnd = queue.dequeue(arity, true);
      tail = bufferStart(tailBufferEnd);
    } while (tail.NE(Deque.TAIL_INITIAL_VALUE) && tail.EQ(tailBufferEnd));

    if (tail.EQ(Deque.TAIL_INITIAL_VALUE))
      return !tailStarved(arity);

      return true;
  }

  /**
   * Close the head buffer and enqueue it at the front of the 
   * shared buffer deque.
   *
   *  @param arity The arity of this buffer.
   */
  private final void closeAndInsertHead(int arity) throws VM_PragmaInline {
    queue.enqueue(head, arity, false);
  }

  /**
   * The tail is empty (or null), and the shared deque has no buffers
   * available.  If the head has sufficient entries, consume the head.
   * Otherwise try wait on the shared deque until either all other
   * clients of the reach exhaustion or a buffer becomes
   * available.
   *
   * @param arity The arity of this buffer  
   * @return True if the consumer has eaten all of the entries
   */
  private final boolean tailStarved(int arity) {
      if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(arity == queue.getArity());
    // entries in tail, so consume tail
    if (!bufferOffset(head).isZero()) {
      tailBufferEnd = head;
      tail = bufferStart(tailBufferEnd);
      head = Deque.HEAD_INITIAL_VALUE;
      return false;
  }

    // Wait for another entry to materialize...
    tailBufferEnd = queue.dequeueAndWait(arity, true);
    tail = bufferStart(tail);

    // return true if a) there is not a tail buffer or b) it is empty
    return (tail.EQ(Deque.TAIL_INITIAL_VALUE) || tail.EQ(tailBufferEnd));
  }
}
