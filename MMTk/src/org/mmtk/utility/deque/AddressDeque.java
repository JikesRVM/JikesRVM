/*
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */
package org.mmtk.utility.deque;

import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of addresses
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class AddressDeque extends LocalDeque 
  implements Constants, Uninterruptible {
   public final static String Id = "$Id$"; 
 
  /****************************************************************************
   *
   * Public instance methods
   */
  public final String name;

  /**
   * Constructor
   *
   * @param queue The shared queue to which this queue will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public AddressDeque(String n, SharedDeque queue) {
    super(queue);
    name = n;
  }

  /**
   * Insert an address into the address queue.
   *
   * @param addr the address to be inserted into the address queue
   */
  public final void insert(Address addr) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!addr.isZero());
    checkTailInsert(1);
    uncheckedTailInsert(addr);
  }

  /**
   * Push an address onto the address queue.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void push(Address addr) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!addr.isZero());
    checkHeadInsert(1);
    uncheckedHeadInsert(addr);
  }

  /**
   * Push an address onto the address queue, force this out of line
   * ("OOL"), in some circumstnaces it is too expensive to have the
   * push inlined, so this call is made.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void pushOOL(Address addr) throws NoInlinePragma {
    push(addr);
  }

  /**
   * Pop an address from the address queue, return zero if the queue
   * is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  public final Address pop() throws InlinePragma {
    if (checkDequeue(1)) {
      return uncheckedDequeue();
    }
    else {
      return Address.zero();
    }
  }

  public final boolean isEmpty() throws InlinePragma {
    return !checkDequeue(1);
  }

  public final boolean isNonEmpty() throws InlinePragma {
    return checkDequeue(1);
  }

}
