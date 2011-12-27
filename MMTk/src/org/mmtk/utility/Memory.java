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
package org.mmtk.utility;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements basic memory copying, setting and clearing
 * operations.
 *
 * NOTE: Most of the operations in this class are performed at teh
 * granularity of a Java integer (ie 4-byte units)
 *
 * FIXME: Why can't these operations be performed at word-granularity?
 */
@Uninterruptible
public class Memory implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  /** zero operations greater than this size are done using the
   * underlying OS implementation of zero() */
  private static final int SMALL_REGION_THRESHOLD = 1<<8; // empirically chosen


  /****************************************************************************
   *
   * Basic memory setting and zeroing operations
   */

  /**
   * Zero a region of memory
   *
   * @param start The start of the region to be zeroed (must be 4-byte aligned)
   * @param bytes The number of bytes to be zeroed (must be 4-byte aligned)
   */
  @Inline
  public static void zero(Address start, Extent bytes) {
    if (VM.VERIFY_ASSERTIONS) {
      assertAligned(start);
      assertAligned(bytes);
    }
    if (bytes.GT(Extent.fromIntZeroExtend(SMALL_REGION_THRESHOLD)))
      VM.memory.zero(false, start, bytes);
    else
      zeroSmall(start, bytes);
  }

  /**
   * Zero a small region of memory
   *
   * @param start The start of the region to be zeroed (must be 4-byte aligned)
   * @param bytes The number of bytes to be zeroed (must be 4-byte aligned)
   */
  @Inline
  public static void zeroSmall(Address start, Extent bytes) {
    if (VM.VERIFY_ASSERTIONS) {
      assertAligned(start);
      assertAligned(bytes);
    }
    Address end = start.plus(bytes);
    for (Address addr = start; addr.LT(end); addr = addr.plus(BYTES_IN_INT))
      addr.store(0);
  }

  /**
   * Set a region of memory
   *
   * @param start The start of the region to be zeroed (must be 4-byte aligned)
   * @param bytes The number of bytes to be zeroed (must be 4-byte aligned)
   * @param value The value to which the integers in the region should be set
   */
  @Inline
  public static void set(Address start, int bytes, int value) {
    if (VM.VERIFY_ASSERTIONS) {
      assertAligned(start);
      assertAligned(bytes);
    }
    Address end = start.plus(bytes);
    for (Address addr = start; addr.LT(end); addr = addr.plus(BYTES_IN_INT))
      addr.store(value);
  }


  /****************************************************************************
   *
   * Helper methods
   */

  /**
   * Check that a memory range is zeroed
   *
   * @param start The start address of the range to be checked
   * @param bytes The size of the region to be checked, in bytes
   * @return True if the region is zeroed
   */
  @Inline
  public static boolean IsZeroed(Address start, int bytes) {
    return isSet(start, bytes, false, 0);
  }

  /**
   * Assert that a memory range is zeroed.  An assertion failure will
   * occur if the region is not zeroed.
   *
   * this is in the inline allocation sequence when
   * VM.VERIFY_ASSERTIONS is true, it is carefully written to
   * reduce the impact on code space.
   *
   * @param start The start address of the range to be checked
   * @param bytes The size of the region to be checked, in bytes
   */
  @NoInline
  public static void assertIsZeroed(Address start, int bytes) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isSet(start, bytes, true, 0));
  }

  /**
   * Verbosely check and return true if a memory range is set to some
   * integer value
   *
   * @param start The start address of the range to be checked
   * @param bytes The size of the region to be checked, in bytes
   * @param value The value to which this region should be set
   * @return True if the region has been correctly set
   */
  @Inline
  public static boolean isSet(Address start, int bytes, int value) {
    return isSet(start, bytes, true, value);
  }

  /**
   * Assert appropriate alignment, triggering an assertion failure if
   * the value does not satisify the alignment requirement of the
   * memory operations.
   *
   * @param value The value to be tested
   */
  private static void assertAligned(int value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((value & (BYTES_IN_INT - 1)) == 0);
  }

  private static void assertAligned(Word value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(value.and(Word.fromIntSignExtend(BYTES_IN_INT-1)).isZero());
  }

  private static void assertAligned(Extent value) {
    assertAligned(value.toWord());
  }

  private static void assertAligned(Address value) {
    assertAligned(value.toWord());
  }

  /**
   * Test whether a memory range is set to a given integer value
   *
   * @param start The address to start checking at
   * @param bytes The size of the region to check, in bytes
   * @param verbose If true, produce verbose output
   * @param value The value to which the memory should be set
   */
  @NoInline
  private static boolean isSet(Address start, int bytes, boolean verbose,
      int value)
    /* Inlining this loop into the uninterruptible code can
     *  cause/encourage the GCP into moving a get_obj_tib into the
     * interruptible region where the tib is being installed via an
     * int_store
   */ {
    if (VM.VERIFY_ASSERTIONS) assertAligned(bytes);
    for (int i = 0; i < bytes; i += BYTES_IN_INT)
      if (start.loadInt(Offset.fromIntSignExtend(i)) != value) {
        if (verbose) {
          Log.prependThreadId();
          Log.write("VM range does not contain only value ");
          Log.writeln(value);
          Log.write("Non-zero range: "); Log.write(start);
          Log.write(" .. "); Log.writeln(start.plus(bytes));
          Log.write("First bad value at "); Log.writeln(start.plus(i));
          dumpMemory(start, 0, bytes);
        }
        return false;
      }
    return true;
  }

  /**
   * Dump the contents of memory around a given address
   *
   * @param addr The address around which the memory should be dumped
   * @param beforeBytes The number of bytes before the address to be
   * included in the dump
   * @param afterBytes The number of bytes after the address to be
   * included in the dump
   */
  public static void dumpMemory(Address addr, int beforeBytes, int afterBytes) {
    VM.memory.dumpMemory(addr, beforeBytes, afterBytes);
  }
}
