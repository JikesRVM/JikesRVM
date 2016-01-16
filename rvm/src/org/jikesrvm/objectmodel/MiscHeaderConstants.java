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
package org.jikesrvm.objectmodel;

import static org.jikesrvm.mm.mminterface.MemoryManagerConstants.GENERATE_GC_TRACE;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;

/**
 * Defines other header words not used for
 * core Java language support of memory allocation.
 * Typically these are extra header words used for various
 * kinds of instrumentation or profiling.
 *
 * @see ObjectModel
 */
public final class MiscHeaderConstants {

  /*********************
   * Support for GC Tracing; uses either 0 or 3 words of MISC HEADER
   */

  /* amount by which tracing causes headers to grow */
  public static final int GC_TRACING_HEADER_WORDS =
      (GENERATE_GC_TRACE ? 3 : 0);
  public static final int GC_TRACING_HEADER_BYTES = GC_TRACING_HEADER_WORDS << LOG_BYTES_IN_ADDRESS;

  /**
   * How many bytes are used by all misc header fields?
   */
  public static final int NUM_BYTES_HEADER = GC_TRACING_HEADER_BYTES; // + YYY_HEADER_BYTES;

  private MiscHeaderConstants() {
    // prevent instantiation
  }

}
