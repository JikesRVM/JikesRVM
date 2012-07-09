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

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that defines a doubly-linked double-ended queue (deque). The
 * double-linking increases the space demands slightly, but makes it far
 * more efficient to dequeue buffers and, for example, enables sorting of
 * its contents.
 */
@Uninterruptible class Deque implements Constants {

  /****************************************************************************
   *
   * Protected instance methods
   *
   * protected int enqueued;
   */

  /**
   *
   */
  @Inline
  protected final Offset bufferOffset(Address buf) {
    return buf.toWord().and(BUFFER_MASK).toOffset();
  }
  @Inline
  protected final Address bufferStart(Address buf) {
    return buf.toWord().and(BUFFER_MASK.not()).toAddress();
  }
  @Inline
  protected final Address bufferEnd(Address buf) {
    return bufferStart(buf).plus(USABLE_BUFFER_BYTES);
  }
  @Inline
  protected final Address bufferFirst(Address buf) {
    return bufferStart(buf);
  }
  @Inline
  protected final Address bufferLast(Address buf, int arity) {
    return bufferStart(buf).plus(bufferLastOffset(arity));
  }
  @Inline
  protected final Address bufferLast(Address buf) {
    return bufferLast(buf, 1);
  }
  @Inline
  protected final Offset bufferLastOffset(int arity) {
    return Offset.fromIntZeroExtend(USABLE_BUFFER_BYTES - BYTES_IN_ADDRESS -
        (USABLE_BUFFER_BYTES % (arity << LOG_BYTES_IN_ADDRESS)));
  }

  /****************************************************************************
   *
   * Private and protected static final fields (aka constants)
   */

  /**
   *
   */
  protected static final int LOG_PAGES_PER_BUFFER = 0;
  protected static final int PAGES_PER_BUFFER = 1 << LOG_PAGES_PER_BUFFER;
  private static final int LOG_BUFFER_SIZE = (LOG_BYTES_IN_PAGE + LOG_PAGES_PER_BUFFER);
  protected static final int BUFFER_SIZE = 1 << LOG_BUFFER_SIZE;
  protected static final Word BUFFER_MASK = Word.one().lsh(LOG_BUFFER_SIZE).minus(Word.one());
  protected static final int NEXT_FIELD_OFFSET = BYTES_IN_ADDRESS;
  protected static final int META_DATA_SIZE = 2 * BYTES_IN_ADDRESS;
  protected static final int USABLE_BUFFER_BYTES = BUFFER_SIZE - META_DATA_SIZE;
  protected static final Address TAIL_INITIAL_VALUE = Address.zero();
  protected static final Address HEAD_INITIAL_VALUE = Address.zero();
}
