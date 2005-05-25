/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_AllocatorHeader;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

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
public interface VM_MiscHeaderConstants extends VM_Constants {

  /*********************
   * Support for GC Tracing; uses either 0 or 3 words of MISC HEADER
   */

  /* amount by which tracing causes headers to grow */
  static final int GC_TRACING_HEADER_WORDS = (VM.CompileForGCTracing ? 3 : 0);
  static final int GC_TRACING_HEADER_BYTES = GC_TRACING_HEADER_WORDS<<LOG_BYTES_IN_ADDRESS;

  /**
   * How many bytes are used by all misc header fields?
   */
  static final int NUM_BYTES_HEADER = GC_TRACING_HEADER_BYTES; // + YYY_HEADER_BYTES;
}
