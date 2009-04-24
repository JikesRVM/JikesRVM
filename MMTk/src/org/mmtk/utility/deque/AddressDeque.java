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
 * This supports <i>unsynchronized</i> enqueuing and dequeuing of addresses
 */
@Uninterruptible public class AddressDeque extends LocalDeque
  implements Constants {

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
  @Inline
  public final void insert(Address addr) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!addr.isZero());
    checkTailInsert(1);
    uncheckedTailInsert(addr);
  }

  /**
   * Insert an address into the address queue, force this out of line
   * ("OOL"), in some circumstnaces it is too expensive to have the
   * insert inlined, so this call is made.
   *
   * @param addr the address to be inserted into the address queue
   */
  @NoInline
  public final void insertOOL(Address addr) {
    insert(addr);
  }

  /**
   * Push an address onto the address queue.
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
   * Push an address onto the address queue, force this out of line
   * ("OOL"), in some circumstnaces it is too expensive to have the
   * push inlined, so this call is made.
   *
   * @param addr the address to be pushed onto the address queue
   */
  @NoInline
  public final void pushOOL(Address addr) {
    push(addr);
  }

  /**
   * Pop an address from the address queue, return zero if the queue
   * is empty.
   *
   * @return The next address in the address queue, or zero if the
   * queue is empty
   */
  @Inline
  public final Address pop() {
    if (checkDequeue(1)) {
      return uncheckedDequeue();
    } else {
      return Address.zero();
    }
  }

  @Inline
  public final boolean isEmpty() {
    return !checkDequeue(1);
  }

  @Inline
  public final boolean isNonEmpty() {
    return checkDequeue(1);
  }

}
