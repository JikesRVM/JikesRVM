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
package org.mmtk.utility;

import org.vmmagic.unboxed.*;

/**
 * The constants needed when storing events and then generating the trace.
 */
public interface TracingConstants {
  Word TRACE_EXACT_ALLOC = Word.zero();
  Word TRACE_BOOT_ALLOC = Word.one().lsh(0);
  Word TRACE_ALLOC = Word.one().lsh(1);
  Word TRACE_DEATH = Word.one().lsh(2);
  Word TRACE_FIELD_SET = Word.one().lsh(3);
  Word TRACE_ARRAY_SET = Word.one().lsh(4);
  Word TRACE_TIB_SET = Word.one().lsh(5);
  Word TRACE_STATIC_SET = Word.one().lsh(6);
  Word TRACE_BOOTSTART = Word.one().lsh(7);
  Word TRACE_BOOTEND = Word.one().lsh(8);
  Word TRACE_GCSTART = Word.one().lsh(9);
  Word TRACE_GCEND = Word.one().lsh(10);
  Word TRACE_GCROOT = Word.one().lsh(11);
  Word TRACE_GCBAR = Word.one().lsh(12);
  Word TRACE_THREAD_SWITCH = Word.one().lsh(13);
  Word TRACE_STACKDELTA = Word.one().lsh(14);
  Word TRACE_ROOTPTR = Word.one().lsh(15);
  Word TRACE_EXACT_IMMORTAL_ALLOC = Word.one().lsh(16);
  Word TRACE_IMMORTAL_ALLOC = Word.one().lsh(17);
}

