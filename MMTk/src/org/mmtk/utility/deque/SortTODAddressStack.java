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

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This supports <i>unsynchronized</i> pushing and popping of addresses.
 * In addition, this can sort the entries currently on the shared stack.
 */
@Uninterruptible public class SortTODAddressStack extends LocalDeque
  implements Constants {

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
    ((SortTODSharedDeque) queue).sort();
  }

  /**
   * Push an address onto the address stack.
   *
   * @param addr the address to be pushed onto the address queue
   */
  @Inline
  public final void push(Address addr) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr.isZero());
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
  @Inline
  public final Address pop() {
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
   *         false otherwise.
   */
  @Inline
  public final boolean isEmpty() {
    return !checkDequeue(1);
  }
}
