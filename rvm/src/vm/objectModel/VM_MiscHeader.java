/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_AllocatorHeader;

/**
 * Defines other header words not used for 
 * core Java language support of memory allocation.
 * Typically these are extra header words used for various
 * kinds of instrumentation or profiling.
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 * @modified <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 */
public final class VM_MiscHeader implements VM_Uninterruptible, VM_Constants {

  /*********************
   * Support for GC Tracing; uses either 0 or 3 words of MISC HEADER
   */

  private static final int MISC_HEADER_START = VM_JavaHeaderConstants.MISC_HEADER_OFFSET;

  /* offset from object ref to .oid field, in bytes */
  static final int OBJECT_OID_OFFSET       = (VM.CompileForGCTracing ? MISC_HEADER_START : 0);
  /* offset from object ref to OBJECT_DEATH field, in bytes */
  static final int OBJECT_DEATH_OFFSET	   = (VM.CompileForGCTracing ? OBJECT_OID_OFFSET + BYTES_IN_ADDRESS : 0);
  /* offset from object ref to .link field, in bytes */
  static final int OBJECT_LINK_OFFSET      = (VM.CompileForGCTracing ? OBJECT_DEATH_OFFSET + BYTES_IN_ADDRESS : 0);
  /* amount by which tracing causes headers to grow */
  static final int GC_TRACING_HEADER_BYTES = (VM.CompileForGCTracing ? (BYTES_IN_ADDRESS*3) : 0);

  /////////////////////////
  // Support for YYY (an example of how to add a word to all objects)
  /////////////////////////  
  // offset from object ref to yet-to-be-defined instrumentation word
  // static final int YYY_DATA_OFFSET_1 = (VM.YYY ? MISC_HEADER_START + GC_TRACING_HEADER_WORDS : 0)
  // static final int YYY_DATA_OFFSET_2 = (VM.YYY ? MISC_HEADER_START + GC_TRACING_HEADER_WORDS + 4 : 0)    
  // static final int YYY_HEADER_BYTES = (VM.YYY ? 8 : 0)

  /**
   * How many bytes are used by all misc header fields?
   */
  static final int NUM_BYTES_HEADER = GC_TRACING_HEADER_BYTES; // + YYY_HEADER_BYTES

  /**
   * How many available bits does the misc header want to use?
   */
  static final int REQUESTED_BITS = 0;

  /**
   * The next object ID to be used.
   */
  private static VM_Word oid;
  /**
   * The current "time" for the trace being generated.
   */
  private static VM_Word time;
  /**
   * The address of the last object allocated into the header.
   */
  private static int prevAddress;

  static {
    oid = VM_Word.fromInt(4);
    time = VM_Word.fromInt(4);
    prevAddress = 0;
  }

  /**
   * Perform any required initialization of the MISC portion of the header.
   * @param obj the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(Object obj, Object[] tib, int size, 
				      boolean isScalar) 
    throws VM_PragmaUninterruptible {
    /* Only perform initialization when it is required */
    if (VM.CompileForGCTracing) {
      VM_Address ref = VM_Magic.objectAsAddress(obj); 
      VM_Magic.setMemoryWord(ref.add(OBJECT_OID_OFFSET), oid);
      VM_Magic.setMemoryWord(ref.add(OBJECT_DEATH_OFFSET), time);
      oid = oid.add(VM_Word.fromInt((size - GC_TRACING_HEADER_BYTES) 
 				    >> LOG_BYTES_IN_ADDRESS));
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
  public static void initializeHeader(BootImageInterface bootImage, int ref, 
                                      Object[] tib, int size, boolean isScalar)
    throws VM_PragmaLogicallyUninterruptible {
    /* Only perform initialization when it is required */
    if (VM.CompileForGCTracing) {
      bootImage.setAddressWord(ref + OBJECT_OID_OFFSET, oid);
      bootImage.setAddressWord(ref + OBJECT_DEATH_OFFSET, time);
      bootImage.setFullWord(ref + OBJECT_LINK_OFFSET, prevAddress);
      prevAddress = ref;
      oid = oid.add(VM_Word.fromInt((size - GC_TRACING_HEADER_BYTES) 
 				    >> LOG_BYTES_IN_ADDRESS));
    }
  }

  public static void updateDeathTime(Object ref) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      VM_Magic.setMemoryWord(VM_Magic.objectAsAddress(ref).add(OBJECT_DEATH_OFFSET), time);
  }

  public static void setDeathTime(VM_Address ref, VM_Word time_) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      VM_Magic.setMemoryWord(ref.add(OBJECT_DEATH_OFFSET), time_);
  }

  public static void setLink(VM_Address ref, VM_Address link) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      VM_Magic.setMemoryAddress(ref.add(OBJECT_LINK_OFFSET), link);
  }

  public static void updateTime(VM_Word time_) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    time = time_;
  }

  public static VM_Word getOID(VM_Address ref) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      return VM_Magic.getMemoryWord(ref.add(OBJECT_OID_OFFSET));
    else
      return VM_Word.zero();
  }

  public static VM_Word getDeathTime(Object ref) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      return VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(ref).add(OBJECT_DEATH_OFFSET));
    else
      return VM_Word.zero();
  }

  public static VM_Address getLink(Object ref) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      return VM_Magic.objectAsAddress(VM_Magic.getObjectAtOffset(ref,
							       OBJECT_LINK_OFFSET));
    else
      return VM_Address.zero();
  }

  public static VM_Address getBootImageLink() {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      return VM_Address.fromInt(prevAddress);
    else
      return VM_Address.zero();
  }

  public static VM_Word getOID() {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      return oid;
    else
      return VM_Word.zero();
  }

  public static void setOID(VM_Word oid_) {
    if (VM.VerifyAssertions) VM._assert(VM.CompileForGCTracing);
    if (VM.CompileForGCTracing)
      oid = oid_;
  }

  public static final int getHeaderSize() {
    return NUM_BYTES_HEADER;
  }


  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) {
    // by default nothing to do, unless the misc header is required
    if (VM.CompileForGCTracing) {
      VM.sysWrite(" OID=", getOID(VM_Magic.objectAsAddress(ref)));
      VM.sysWrite(" LINK=", getLink(ref));
      VM.sysWrite(" DEATH=", getDeathTime(ref));
    }
  }
}
