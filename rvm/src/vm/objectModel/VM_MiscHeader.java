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
 */
public final class VM_MiscHeader implements VM_Uninterruptible {

  ///////////////////////
  // Support for GC Tracing; uses either 0 or 3 words of MISC HEADER
  ///////////////////////

  private static final int MISC_HEADER_START = VM_JavaHeaderConstants.MISC_HEADER_OFFSET;

  // offset from object ref to .oid field, in bytes
  static final int OBJECT_OID_OFFSET       = (VM.CompileForGCTracing ? MISC_HEADER_START - 4 : 0);
  // offset from object ref to .link field, in bytes
  static final int OBJECT_LINK_OFFSET      = (VM.CompileForGCTracing ? MISC_HEADER_START - 8 : 0);
  // offset from object ref to OBJECT_DEATH field, in bytes
  static final int OBJECT_DEATH_OFFSET     = (VM.CompileForGCTracing ? MISC_HEADER_START - 12 : 0);
  // amount by which tracing causes headers to grow
  static final int GC_TRACING_HEADER_BYTES = (VM.CompileForGCTracing ? 12 : 0);

  /////////////////////////
  // Support for YYY (an example of how to add a word to all objects)
  /////////////////////////  
  // offset from object ref to yet-to-be-defined instrumentation word
  // static final int YYY_DATA_OFFSET = (VM.YYY ? MISC_HEADER_START - GC_TRACING_HEADER_WORDS : 0)
  // static final int YYY_HEADER_BYTES = (VM.YYY ? 4 : 0)

  /**
   * How many bytes are used by all misc header fields?
   */
  static final int NUM_BYTES_HEADER = GC_TRACING_HEADER_BYTES; // + YYY_HEADER_BYTES

  /**
   * How many available bits does the misc header want to use?
   */
  static final int REQUESTED_BITS = 0;

  /**
   * Perform any required initialization of the MISC portion of the header.
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(Object ref, Object[] tib, int size, boolean isScalar) {
    // by default, nothing to do
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
                                      Object[] tib, int size, boolean isScalar) {
    // by default, nothing to do
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(Object ref) {
    // be default nothing to do.
  }
}
