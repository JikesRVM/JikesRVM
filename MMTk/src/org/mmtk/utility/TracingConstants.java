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
  static final Word TRACE_EXACT_ALLOC          = Word.zero();
  static final Word TRACE_BOOT_ALLOC           = Word.one().lsh(0);
  static final Word TRACE_ALLOC                = Word.one().lsh(1);
  static final Word TRACE_DEATH                = Word.one().lsh(2);
  static final Word TRACE_FIELD_SET            = Word.one().lsh(3);
  static final Word TRACE_ARRAY_SET            = Word.one().lsh(4);
  static final Word TRACE_TIB_SET              = Word.one().lsh(5);
  static final Word TRACE_STATIC_SET           = Word.one().lsh(6);
  static final Word TRACE_BOOTSTART            = Word.one().lsh(7);
  static final Word TRACE_BOOTEND              = Word.one().lsh(8);
  static final Word TRACE_GCSTART              = Word.one().lsh(9);
  static final Word TRACE_GCEND                = Word.one().lsh(10);
  static final Word TRACE_GCROOT               = Word.one().lsh(11);
  static final Word TRACE_GCBAR                = Word.one().lsh(12);
  static final Word TRACE_THREAD_SWITCH        = Word.one().lsh(13);
  static final Word TRACE_STACKDELTA           = Word.one().lsh(14);
  static final Word TRACE_ROOTPTR              = Word.one().lsh(15);
  static final Word TRACE_EXACT_IMMORTAL_ALLOC = Word.one().lsh(16);
  static final Word TRACE_IMMORTAL_ALLOC       = Word.one().lsh(17);
}

