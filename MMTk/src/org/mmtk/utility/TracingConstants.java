/**
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003.
 */

package org.mmtk.utility;

import org.vmmagic.unboxed.*;

/**
 * The constants needed when storing events and then generating the trace.
 *
 * $Id$
 *
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */ 
public interface TracingConstants {
  static final Word TRACE_EXACT_ALLOC = Word.fromInt(0);
  static final Word TRACE_BOOT_ALLOC = Word.fromInt(1);
  static final Word TRACE_ALLOC = Word.fromInt(2);
  static final Word TRACE_DEATH = Word.fromInt(4);
  static final Word TRACE_FIELD_SET = Word.fromInt(8);
  static final Word TRACE_ARRAY_SET = Word.fromInt(16);
  static final Word TRACE_TIB_SET = Word.fromInt(32);
  static final Word TRACE_STATIC_SET = Word.fromInt(64);
  static final Word TRACE_BOOTSTART = Word.fromInt(128);
  static final Word TRACE_BOOTEND = Word.fromInt(256);
  static final Word TRACE_GCSTART = Word.fromInt(512);
  static final Word TRACE_GCEND = Word.fromInt(1024);
  static final Word TRACE_GCROOT = Word.fromInt(2048);
  static final Word TRACE_GCBAR = Word.fromInt(4096);
  static final Word TRACE_THREAD_SWITCH = Word.fromInt(8192);
  static final Word TRACE_STACKDELTA = Word.fromInt(16384);
  static final Word TRACE_ROOTPTR = Word.fromInt(32768);
  static final Word TRACE_EXACT_IMMORTAL_ALLOC = Word.fromInt(65536);
  static final Word TRACE_IMMORTAL_ALLOC = Word.fromInt(131072);
}

