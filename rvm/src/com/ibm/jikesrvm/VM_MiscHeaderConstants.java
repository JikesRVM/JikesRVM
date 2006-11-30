/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm;

import com.ibm.jikesrvm.memorymanagers.mminterface.MM_Interface;

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
  static final int GC_TRACING_HEADER_WORDS = (MM_Interface.GENERATE_GC_TRACE ? 3 : 0);
  static final int GC_TRACING_HEADER_BYTES = GC_TRACING_HEADER_WORDS<<LOG_BYTES_IN_ADDRESS;

  /**
   * How many bytes are used by all misc header fields?
   */
  static final int NUM_BYTES_HEADER = GC_TRACING_HEADER_BYTES; // + YYY_HEADER_BYTES;
}
