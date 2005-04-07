/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of
 * address pairs
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class AddressPairDeque extends LocalDeque implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 
 
  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared queue to which this queue will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public AddressPairDeque(SharedDeque queue) {
    super(queue);
  }
 
  /**
   * Insert an address pair into the address queue.
   *
   * @param addr1 the first address to be inserted into the address queue
   * @param addr2 the second address to be inserted into the address queue
   */
  public final void insert(Address addr1, Address addr2) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!addr1.isZero());
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!addr2.isZero());
    checkTailInsert(2);
    uncheckedTailInsert(addr1);
    uncheckedTailInsert(addr2);
  }
 
  /**
   * Push an address pair onto the address queue.
   *
   * @param addr1 the first value to be pushed onto the address queue
   * @param addr2 the second value to be pushed onto the address queue
   */
  public final void push(Address addr1, Address addr2) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!addr1.isZero());
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!addr2.isZero());
    checkHeadInsert(2);
    uncheckedHeadInsert(addr2);
    uncheckedHeadInsert(addr1);
  }

  /**
   * Pop the first address in a pair from the address queue, return
   * zero if the queue is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final Address pop1() {
     if (checkDequeue(2))
      return uncheckedDequeue();
    else
      return Address.zero();
  }
  
  /**
   * Pop the second address in a pair from the address queue.
   *
   * @return The next address in the address queue
   */
  public final Address pop2() {
    return uncheckedDequeue();
  }

  public final boolean isEmpty() {
    return !checkDequeue(2);
  }
}
