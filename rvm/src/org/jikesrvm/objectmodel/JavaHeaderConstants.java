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
import static org.jikesrvm.mm.mminterface.MemoryManagerConstants.NEEDS_LINEAR_SCAN;
import static org.jikesrvm.objectmodel.MiscHeaderConstants.NUM_BYTES_HEADER;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_INT;
import static org.jikesrvm.runtime.JavaSizeConstants.LOG_BYTES_IN_INT;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Constants for the JavaHeader.
 *
 * @see ObjectModel
 */
public final class JavaHeaderConstants {

  /** Number of bytes in object's TIB pointer */
  public static final int TIB_BYTES = BYTES_IN_ADDRESS;
  /** Number of bytes indicating an object's status */
  public static final int STATUS_BYTES = BYTES_IN_ADDRESS;

  public static final int ALIGNMENT_MASK = 0x00000001;
  public static final int ALIGNMENT_VALUE = 0xdeadbeef;
  public static final int LOG_MIN_ALIGNMENT = LOG_BYTES_IN_INT;

  /**
   * Number of bytes used to store the array length. We use 64 bits
   * for the length on a 64 bit architecture as this makes the other
   * words 8-byte aligned, and the header has to be 8-byte aligned.
   */
  public static final int ARRAY_LENGTH_BYTES = VM.BuildFor64Addr ? BYTES_IN_ADDRESS : BYTES_IN_INT;

  /** Number of bytes used by the Java Header */
  public static final int JAVA_HEADER_BYTES = TIB_BYTES + STATUS_BYTES;
  /** Number of bytes used by the GC Header */
  public static final int GC_HEADER_BYTES = MemoryManagerConstants.GC_HEADER_BYTES;
  /** Number of bytes used by the miscellaneous header */
  public static final int MISC_HEADER_BYTES = NUM_BYTES_HEADER;
  /** Size of GC and miscellaneous headers */
  public static final int OTHER_HEADER_BYTES = GC_HEADER_BYTES + MISC_HEADER_BYTES;

  /** Offset of array length from object reference */
  public static final Offset ARRAY_LENGTH_OFFSET = Offset.fromIntSignExtend(-ARRAY_LENGTH_BYTES);
  /** Offset of the first field from object reference */
  public static final Offset FIELD_ZERO_OFFSET = ARRAY_LENGTH_OFFSET;
  /** Offset of the Java header from the object reference */
  public static final Offset JAVA_HEADER_OFFSET = ARRAY_LENGTH_OFFSET.minus(JAVA_HEADER_BYTES);
  /** Offset of the miscellaneous header from the object reference */
  public static final Offset MISC_HEADER_OFFSET = JAVA_HEADER_OFFSET.minus(MISC_HEADER_BYTES);
  /** Offset of the garbage collection header from the object reference */
  public static final Offset GC_HEADER_OFFSET = MISC_HEADER_OFFSET.minus(GC_HEADER_BYTES);
  /** Offset of first element of an array */
  public static final Offset ARRAY_BASE_OFFSET = Offset.zero();

  /**
   * This object model supports two schemes for hashcodes:
   * (1) a 10 bit hash code in the object header
   * (2) use the address of the object as its hashcode.
   *     In a copying collector, this forces us to add a word
   *     to copied objects that have had their hashcode taken.
   */
  public static final boolean ADDRESS_BASED_HASHING = !GENERATE_GC_TRACE;

  /** How many bits in the header are available for the GC and MISC headers? */
  public static final int NUM_AVAILABLE_BITS = ADDRESS_BASED_HASHING ? 8 : 2;

  /**
   * Does this object model use the same header word to contain
   * the TIB and a forwarding pointer?
   */
  public static final boolean FORWARDING_PTR_OVERLAYS_TIB = false;

  /**
   * Does this object model place the hash for a hashed and moved object
   * after the data (at a dynamic offset)
   */
  public static final boolean DYNAMIC_HASH_OFFSET = ADDRESS_BASED_HASHING && NEEDS_LINEAR_SCAN;

  /**
   * Can we perform a linear scan?
   */
  public static final boolean ALLOWS_LINEAR_SCAN = true;

  /**
   * Do we need to segregate arrays and scalars to do a linear scan?
   */
  public static final boolean SEGREGATE_ARRAYS_FOR_LINEAR_SCAN = false;

  /*
   * Stuff for address based hashing
   */
  public static final Word HASH_STATE_UNHASHED = Word.zero();
  public static final Word HASH_STATE_HASHED = Word.one().lsh(8); //0x00000100
  public static final Word HASH_STATE_HASHED_AND_MOVED = Word.fromIntZeroExtend(3).lsh(8); //0x0000300
  public static final Word HASH_STATE_MASK = HASH_STATE_UNHASHED.or(HASH_STATE_HASHED).or(HASH_STATE_HASHED_AND_MOVED);

  public static final int HASHCODE_BYTES = BYTES_IN_INT;
  public static final Offset HASHCODE_OFFSET = GC_HEADER_OFFSET.minus(HASHCODE_BYTES);

}
