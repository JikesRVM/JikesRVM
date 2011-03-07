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
package org.jikesrvm.mm.mminterface;

import static org.jikesrvm.SizeConstants.LOG_BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.objectmodel.JavaHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Word;

/**
 * Support for encoding a small amount of metadata in the alignment of
 * a TIB.  We choose the alignment of the TIB so that the pointer
 * looks like
 * <pre>
 *     31      24      16      8       0
 *     +-------+-------+-------+-------+
 *     xxxxxxxxxxxxxxxxxxxxxxxxxxxxfff00
 * </pre>
 * where the natural alignment of the object is preserved (the low-order bits
 * are zero), and the next least significant <i>n</i> bits contain the
 * encoded metadata.
 * <p>
 * With the cooperation of MemoryManager, the idea is that we allocate 2^n
 * additional words of memory, then offset the object within the allocated
 * region so that the value of <i>fff</i> is encoded.
 * <p>
 * The current implementation specifically encodes the TIB pointer, because this
 * is the only pointer field where this technique can give a speedup that
 * makes it worthwhile.
 */
public class AlignmentEncoding {

  public static final int ALIGN_CODE_NONE = -1;

  /** Bits of metadata that we encode */
  static final int FIELD_WIDTH = 3;

  /** Maximum distance (in words) that we shift an object */
  private static final int MAX_ALIGN_WORDS = 1 << FIELD_WIDTH;

  /** First bit of the encoded field */
  private static final int FIELD_SHIFT = LOG_BYTES_IN_ADDRESS;

  /** How far do we need to shift the object to increment the encoded field by 1 */
  private static final int ALIGNMENT_INCREMENT = 1 << FIELD_SHIFT;

  /** Bit-mask to select out the encoded field */
  private static final int TIB_ALIGN_MASK = (MAX_ALIGN_WORDS - 1) << FIELD_SHIFT;

  private static final boolean VERBOSE = false;

  /**
   * Assert that a prospective encoded value is sane
   * @param alignCode Prospective encoded value
   */
  static void assertSanity(int alignCode) {
    if (VM.VerifyAssertions)  {
      VM._assert(alignCode == ALIGN_CODE_NONE || (alignCode >= 0 && alignCode < MAX_ALIGN_WORDS));
    }
  }

  /**
   * Number of padding bytes required.
   * @param alignCode Prospective encoded value.
   * @return
   */
  public static int padding(int alignCode) {
    if (alignCode == ALIGN_CODE_NONE)
      return 0;
    return (MAX_ALIGN_WORDS << FIELD_SHIFT);
  }

  /**
   * Adjust a region address so that the object pointer of an object that starts at this address
   * will be aligned so as to encode the specified value.
   *
   * @param alignCode Value to encode
   * @param region The initial region
   * @return
   */
  public static Address adjustRegion(int alignCode, Address region) {
    assertSanity(alignCode);
    if (alignCode == ALIGN_CODE_NONE)
      return region;
    // Now fake the region address to encode our data
    final Address limit = region.plus(padding(alignCode));
    if (VERBOSE) {
      VM.sysWrite("Allocating TIB: region = ",region," tib code = ",getTibCodeForRegion(region));
      VM.sysWriteln(", requested = ",alignCode);
    }
    while (getTibCodeForRegion(region) != alignCode) {
      if (VM.runningVM) {
        // Hack to allow alignment, but no alignment filling during boot
        region.store(Word.fromIntZeroExtend(ObjectModel.ALIGNMENT_VALUE));
      }
      region = region.plus(ALIGNMENT_INCREMENT);
      if (region.GT(limit)) {
        VM.sysFail("Tib alignment fail");
      }
    }
    if (VERBOSE) {
      VM.sysWrite("           TIB: region = ",region," tib code = ",getTibCodeForRegion(region));
      VM.sysWriteln(", requested = ",alignCode);
    }
    return region;
  }


  private static int getTibCodeForRegion(Address region) {
    return extractTibCode(region.plus(JavaHeader.OBJECT_REF_OFFSET));
  }

  /**
   * Extract the encoded value from a TIB pointer,
   * represented as a raw address.
   * @param tib
   * @return
   */
  @Uninterruptible
  @Inline
  public static int extractTibCode(Address address) {
    return (address.toInt() & TIB_ALIGN_MASK) >> FIELD_SHIFT;
  }

  /**
   * Extract the encoded value from an object's TIB pointer
   * @param tib
   * @return
   */
  @Uninterruptible
  @Inline
  public static int getTibCode(ObjectReference object) {
    int tibCode = extractTibCode(Magic.objectAsAddress(ObjectModel.getTIB(object)));
    return tibCode;
  }

}
