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

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.mm.mminterface.MemoryManagerConstants;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Defines other header words not used for
 * core Java language support of memory allocation.
 * Typically these are extra header words used for various
 * kinds of instrumentation or profiling.
 *
 * @see ObjectModel
 */
@Uninterruptible
public final class MiscHeader implements Constants, MiscHeaderConstants {

  private static final Offset MISC_HEADER_START = JavaHeaderConstants.MISC_HEADER_OFFSET;

  /* offset from object ref to .oid field, in bytes */
  static final Offset OBJECT_OID_OFFSET = MISC_HEADER_START;
  /* offset from object ref to OBJECT_DEATH field, in bytes */
  static final Offset OBJECT_DEATH_OFFSET = OBJECT_OID_OFFSET.plus(BYTES_IN_ADDRESS);
  /* offset from object ref to .link field, in bytes */
  static final Offset OBJECT_LINK_OFFSET = OBJECT_DEATH_OFFSET.plus(BYTES_IN_ADDRESS);

  /////////////////////////
  // Support for YYY (an example of how to add a word to all objects)
  /////////////////////////
  // offset from object ref to yet-to-be-defined instrumentation word
  // static final int YYY_DATA_OFFSET_1 = (VM.YYY ? MISC_HEADER_START + GC_TRACING_HEADER_WORDS : 0);
  // static final int YYY_DATA_OFFSET_2 = (VM.YYY ? MISC_HEADER_START + GC_TRACING_HEADER_WORDS + 4 : 0);
  // static final int YYY_HEADER_BYTES = (VM.YYY ? 8 : 0);

  /**
   * How many available bits does the misc header want to use?
   */
  static final int REQUESTED_BITS = 0;

  /**
   * The next object ID to be used.
   */
  @Entrypoint
  private static Word oid;
  /**
   * The current "time" for the trace being generated.
   */
  private static Word time;
  /**
   * The address of the last object allocated into the header.
   */
  @Entrypoint
  private static Word prevAddress;

  static {
    oid = Word.fromIntSignExtend(4);
    time = Word.fromIntSignExtend(4);
    prevAddress = Word.zero();
  }

  /**
   * Perform any required initialization of the MISC portion of the header.
   * @param obj the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  @Uninterruptible
  public static void initializeHeader(Object obj, TIB tib, int size, boolean isScalar) {
    /* Only perform initialization when it is required */
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      Address ref = Magic.objectAsAddress(obj);
      ref.store(oid, OBJECT_OID_OFFSET);
      ref.store(time, OBJECT_DEATH_OFFSET);
      oid = oid.plus(Word.fromIntSignExtend((size - GC_TRACING_HEADER_BYTES) >> LOG_BYTES_IN_ADDRESS));
    }
  }

  /**
   * Perform any required initialization of the MISC portion of the header.
   * @param bootImage the bootimage being written
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  @Interruptible("Only called during boot iamge creation")
  public static void initializeHeader(BootImageInterface bootImage, Address ref, TIB tib, int size,
                                      boolean isScalar) {
    /* Only perform initialization when it is required */
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      bootImage.setAddressWord(ref.plus(OBJECT_OID_OFFSET), oid, false, false);
      bootImage.setAddressWord(ref.plus(OBJECT_DEATH_OFFSET), time, false, false);
      bootImage.setAddressWord(ref.plus(OBJECT_LINK_OFFSET), prevAddress, false, false);
      prevAddress = ref.toWord();
      oid = oid.plus(Word.fromIntSignExtend((size - GC_TRACING_HEADER_BYTES) >> LOG_BYTES_IN_ADDRESS));
    }
  }

  public static void updateDeathTime(Object object) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      Magic.objectAsAddress(object).store(time, OBJECT_DEATH_OFFSET);
    }
  }

  public static void setDeathTime(Object object, Word time_) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      Magic.objectAsAddress(object).store(time_, OBJECT_DEATH_OFFSET);
    }
  }

  public static void setLink(Object object, ObjectReference link) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      Magic.objectAsAddress(object).store(link, OBJECT_LINK_OFFSET);
    }
  }

  public static void updateTime(Word time_) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    time = time_;
  }

  public static Word getOID(Object object) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      return Magic.objectAsAddress(object).plus(OBJECT_OID_OFFSET).loadWord();
    } else {
      return Word.zero();
    }
  }

  public static Word getDeathTime(Object object) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      return Magic.objectAsAddress(object).plus(OBJECT_DEATH_OFFSET).loadWord();
    } else {
      return Word.zero();
    }
  }

  public static ObjectReference getLink(Object ref) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      return ObjectReference.fromObject(Magic.getObjectAtOffset(ref, OBJECT_LINK_OFFSET));
    } else {
      return ObjectReference.nullReference();
    }
  }

  public static Address getBootImageLink() {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      return prevAddress.toAddress();
    } else {
      return Address.zero();
    }
  }

  public static Word getOID() {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      return oid;
    } else {
      return Word.zero();
    }
  }

  public static void setOID(Word oid_) {
    if (VM.VerifyAssertions) VM._assert(MemoryManagerConstants.GENERATE_GC_TRACE);
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      oid = oid_;
    }
  }

  public static int getHeaderSize() {
    return NUM_BYTES_HEADER;
  }

  /**
   * For low level debugging of GC subsystem.
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped
   */
  public static void dumpHeader(Object ref) {
    // by default nothing to do, unless the misc header is required
    if (MemoryManagerConstants.GENERATE_GC_TRACE) {
      VM.sysWrite(" OID=", getOID(ref));
      VM.sysWrite(" LINK=", getLink(ref));
      VM.sysWrite(" DEATH=", getDeathTime(ref));
    }
  }
}
