/*
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003
 */
package org.mmtk.utility.deque;

import org.mmtk.vm.Assert;
import org.mmtk.vm.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> pushing and popping of addresses. 
 * In addition, this can sort the entries currently on the shared stack.
 *
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public class SortTODAddressStack extends LocalDeque 
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 
 
  /****************************************************************************
   *
   * Public instance methods
   */

  /**
   * Constructor
   *
   * @param queue The shared stack to which this stack will append
   * its buffers (when full or flushed) and from which it will aquire new
   * buffers when it has exhausted its own.
   */
  public SortTODAddressStack(SortTODSharedDeque queue) {
    super(queue);
  }

   /**
   * Sort the address on the shared stack.
   */ 
  public final void sort() {
    flushLocal();  
    ((SortTODSharedDeque)queue).sort();
  }

  /**
   * Push an address onto the address stack.
   *
   * @param addr the address to be pushed onto the address queue
   */
  public final void push(Address addr) throws InlinePragma {
    Assert._assert(!addr.isZero());
    checkHeadInsert(1);
    uncheckedHeadInsert(addr);
  }

  /**
   * Pop an address from the address stack, return zero if the stack
   * is empty.
   *
   * @return The next address in the address stack, or zero if the
   * stack is empty
   */
  public final Address pop() throws InlinePragma {
    if (checkDequeue(1)) {
      return uncheckedDequeue();
    } else {
      return Address.zero();
    }
  }

  /**
   * Check if the (local and shared) stacks are empty.
   *
   * @return True if there are no more entries on the local & shared stack,
   * false otherwise.
   */
  public final boolean isEmpty() throws InlinePragma {
    return !checkDequeue(1);
  }
}
