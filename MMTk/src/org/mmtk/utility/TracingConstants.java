/**
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003.
 */

package org.mmtk.utility;

import com.ibm.JikesRVM.VM_Word;

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
  static final VM_Word TRACE_EXACT_ALLOC = VM_Word.fromInt(0);
  static final VM_Word TRACE_BOOT_ALLOC = VM_Word.fromInt(1);
  static final VM_Word TRACE_ALLOC = VM_Word.fromInt(2);
  static final VM_Word TRACE_DEATH = VM_Word.fromInt(4);
  static final VM_Word TRACE_FIELD_SET = VM_Word.fromInt(8);
  static final VM_Word TRACE_ARRAY_SET = VM_Word.fromInt(16);
  static final VM_Word TRACE_TIB_SET = VM_Word.fromInt(32);
  static final VM_Word TRACE_STATIC_SET = VM_Word.fromInt(64);
  static final VM_Word TRACE_BOOTSTART = VM_Word.fromInt(128);
  static final VM_Word TRACE_BOOTEND = VM_Word.fromInt(256);
  static final VM_Word TRACE_GCSTART = VM_Word.fromInt(512);
  static final VM_Word TRACE_GCEND = VM_Word.fromInt(1024);
  static final VM_Word TRACE_GCROOT = VM_Word.fromInt(2048);
  static final VM_Word TRACE_GCBAR = VM_Word.fromInt(4096);
  static final VM_Word TRACE_THREAD_SWITCH = VM_Word.fromInt(8192);
  static final VM_Word TRACE_STACKDELTA = VM_Word.fromInt(16384);
  static final VM_Word TRACE_ROOTPTR = VM_Word.fromInt(32768);
  static final VM_Word TRACE_EXACT_IMMORTAL_ALLOC = VM_Word.fromInt(65536);
  static final VM_Word TRACE_IMMORTAL_ALLOC = VM_Word.fromInt(131072);
}

