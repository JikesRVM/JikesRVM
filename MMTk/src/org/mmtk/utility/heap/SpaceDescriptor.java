/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.heap;

import org.mmtk.policy.Space;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class manages the encoding and decoding of space descriptors.<p>
 *
 * Space descriptors are integers that encode a space's mapping into
 * virtual memory.  For discontiguous spaces, they indicate
 * discontiguity and mapping must be done by consulting the space map.
 * For contiguous spaces, the space's address range is encoded into
 * the integer (using a fixed point notation).<p>
 *
 * The purpose of this class is to allow <code>static final int</code>
 * space descriptors to exist for each space, which can then be used
 * in tests to determine whether an object is in a space.  A good
 * compiler can perform this decoding at compile time and produce
 * optimal code for the test.
 */
@Uninterruptible public class SpaceDescriptor implements Constants {

  /****************************************************************************
   *
   * Class variables
   */

  private static final int TYPE_BITS = 2;
  private static final int TYPE_SHARED = 0;
  private static final int TYPE_CONTIGUOUS = 1;
  private static final int TYPE_CONTIGUOUS_HI = 3;
  private static final int TYPE_MASK = (1 << TYPE_BITS) - 1;
  private static final int SIZE_SHIFT = TYPE_BITS;
  private static final int SIZE_BITS = 10;
  private static final int SIZE_MASK = ((1 << SIZE_BITS) - 1) << SIZE_SHIFT;
  private static final int EXPONENT_SHIFT = SIZE_SHIFT + SIZE_BITS;
  private static final int EXPONENT_BITS = 5;
  private static final int EXPONENT_MASK = ((1 << EXPONENT_BITS) - 1) << EXPONENT_SHIFT;
  private static final int MANTISSA_SHIFT = EXPONENT_SHIFT + EXPONENT_BITS;
  private static final int MANTISSA_BITS = 14;
  private static final int BASE_EXPONENT = BITS_IN_INT - MANTISSA_BITS;

  private static int discontiguousSpaceIndex = 0;
  private static final int DISCONTIG_INDEX_INCREMENT = 1<<TYPE_BITS;

  /****************************************************************************
   *
   * Descriptor creation
   */

  /**
   * Create a descriptor for a <i>contiguous</i> space
   *
   * @param start The start address of the space
   * @param end The end address of the space
   * @return An integer descriptor encoding the region of virtual
   * memory occupied by the space
   */
  public static int createDescriptor(Address start, Address end) {
    int chunks = end.diff(start).toWord().rshl(Space.LOG_BYTES_IN_CHUNK).toInt();
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(!start.isZero() && chunks > 0 && chunks < (1 << SIZE_BITS));
    boolean top = end.EQ(Space.HEAP_END);
    Word tmp = start.toWord();
    tmp = tmp.rshl(BASE_EXPONENT);
    int exponent = 0;
    while (!tmp.isZero() && tmp.and(Word.one()).isZero()) {
      tmp = tmp.rshl(1);
      exponent++;
    }
    int mantissa = tmp.toInt();
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(tmp.lsh(BASE_EXPONENT + exponent).EQ(start.toWord()));
    return (mantissa<<MANTISSA_SHIFT) |
           (exponent<<EXPONENT_SHIFT) |
           (chunks << SIZE_SHIFT) |
           ((top) ? TYPE_CONTIGUOUS_HI : TYPE_CONTIGUOUS);
  }

  /**
   * Create a descriptor for a <i>dis-contiguous</i> (shared) space
   *
   * @return An integer descriptor reflecting the fact that this space
   * is shared (and thus discontiguous and so must be established via
   * maps).
   */
  public static int createDescriptor() {
    discontiguousSpaceIndex += DISCONTIG_INDEX_INCREMENT;
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert((discontiguousSpaceIndex & TYPE_CONTIGUOUS) != TYPE_CONTIGUOUS);
    return discontiguousSpaceIndex;
  }

  /****************************************************************************
   *
   * Descriptor interrogation
   */

  /**
   * Return true if this descriptor describes a contiguous space
   *
   * @param descriptor
   * @return True if this descriptor describes a contiguous space
   */
  @Inline
  public static boolean isContiguous(int descriptor) {
    return ((descriptor & TYPE_CONTIGUOUS) == TYPE_CONTIGUOUS);
  }

  /**
   * Return true if this descriptor describes a contiguous space that
   * is at the top of the virtual address space
   *
   * @param descriptor
   * @return True if this descriptor describes a contiguous space that
   * is at the top of the virtual address space
   */
  @Inline
  public static boolean isContiguousHi(int descriptor) {
    return ((descriptor & TYPE_MASK) == TYPE_CONTIGUOUS_HI);
  }

  /**
   * Return the start of this region of memory encoded in this descriptor
   *
   * @param descriptor
   * @return The start of this region of memory encoded in this descriptor
   */
  @Inline
  public static Address getStart(int descriptor) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isContiguous(descriptor));
    Word mantissa = Word.fromIntSignExtend(descriptor >>> MANTISSA_SHIFT);
    int exponent = (descriptor & EXPONENT_MASK) >>> EXPONENT_SHIFT;
    return mantissa.lsh(BASE_EXPONENT + exponent).toAddress();
  }

  /**
   * Return the size of the region of memory encoded in this
   * descriptor, in chunks
   *
   * @param descriptor
   * @return The size of the region of memory encoded in this
   * descriptor, in chunks
   */
  @Inline
  public static int getChunks(int descriptor) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isContiguous(descriptor));
    return (descriptor & SIZE_MASK) >>> SIZE_SHIFT;
  }
}
