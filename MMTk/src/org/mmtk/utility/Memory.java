/*
 * (C) Copyright IBM Corp. 2001
 */
package org.mmtk.utility;

import org.mmtk.vm.Assert;

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
 *
 *  $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Memory implements Uninterruptible, Constants {

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
  public static void zero(Address start, Extent bytes) throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      assertAligned(start.toInt());
      assertAligned(bytes.toInt());
    }
    if (bytes.GT(Extent.fromIntZeroExtend(SMALL_REGION_THRESHOLD))) 
      org.mmtk.vm.Memory.zero(start, bytes);
    else
      zeroSmall(start, bytes);
  }

  /**
   * Zero a page-aligned region of memory
   *
   * @param start The start of the region to be zeroed (must be page aligned)
   * @param bytes The number of bytes to be zeroed (must be page aligned)
   */
  public static void zeroPages(Address start, int bytes) throws InlinePragma {
    org.mmtk.vm.Memory.zeroPages(start, bytes);
  }

  /**
   * Zero a small region of memory
   *
   * @param start The start of the region to be zeroed (must be 4-byte aligned)
   * @param bytes The number of bytes to be zeroed (must be 4-byte aligned)
   */
  public static void zeroSmall(Address start, Extent bytes) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      assertAligned(start.toInt());
      assertAligned(bytes.toInt());
    }
    Address end = start.add(bytes);
    for (Address addr = start; addr.LT(end); addr = addr.add(BYTES_IN_INT)) 
      addr.store(0);
  }

  /**
   * Set a region of memory
   *
   * @param start The start of the region to be zeroed (must be 4-byte aligned)
   * @param bytes The number of bytes to be zeroed (must be 4-byte aligned)
   * @param value The value to which the integers in the region should be set
   */
  public static void set(Address start, int bytes, int value)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) {
      assertAligned(start.toInt());
      assertAligned(bytes);
    }
    Address end = start.add(bytes);
    for (Address addr = start; addr.LT(end); addr = addr.add(BYTES_IN_INT)) 
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
  public static boolean IsZeroed(Address start, int bytes)
    throws InlinePragma {
    return isSet(start, bytes, false, 0);
  }

  /**
   * Assert that a memory range is zeroed.  An assertion failure will
   * occur if the region is not zeroed.
   *
   * this is in the inline allocation sequence when
   * Assert.VERIFY_ASSERTIONS is true, it is carefully written to
   * reduce the impact on code space.
   *
   * @param start The start address of the range to be checked
   * @param bytes The size of the region to be checked, in bytes
   */
  public static void assertIsZeroed(Address start, int bytes) 
    throws NoInlinePragma {
    Assert._assert(isSet(start, bytes, true, 0));
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
  public static boolean isSet(Address start, int bytes, int value) 
    throws InlinePragma {
    return isSet(start, bytes, true, value);
  }

  /**
   * Assert appropriate alignment, triggering an assertion failure if
   * the value does not satisify the alignment requirement of the
   * memory operations.
   *
   * @param value The value to be tested
   */
  private static final void assertAligned(int value) {
    Assert._assert((value & (BYTES_IN_INT-1)) == 0);
  }
  
  /**
   * Test whether a memory range is set to a given integer value
   *
   * @param start The address to start checking at
   * @param bytes The size of the region to check, in bytes
   * @param verbose If true, produce verbose output
   * @param value The value to which the memory should be set
   */
  private static boolean isSet(Address start, int bytes, boolean verbose, 
                               int value)
    /* Inlining this loop into the uninterruptible code can
     *  cause/encourage the GCP into moving a get_obj_tib into the
     * interruptible region where the tib is being installed via an
     * int_store
     */
    throws NoInlinePragma {
    if (Assert.VERIFY_ASSERTIONS) assertAligned(bytes);
    for (int i=0; i < bytes; i += BYTES_IN_INT) 
      if (start.loadInt(Offset.fromInt(i)) != value) {
        if (verbose) {
          Log.prependThreadId();
          Log.write("Memory range does not contain only value ");
          Log.writeln(value);
          Log.write("Non-zero range: "); Log.write(start);
          Log.write(" .. "); Log.writeln(start.add(bytes));
          Log.write("First bad value at "); Log.writeln(start.add(i));
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
  public static void dumpMemory(Address addr, int beforeBytes, int afterBytes)
  {
    org.mmtk.vm.Memory.dumpMemory(addr, beforeBytes, afterBytes);
  }
}
