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
public final class TracingConstants {
  public static final Word TRACE_EXACT_ALLOC = Word.zero();
  public static final Word TRACE_BOOT_ALLOC = Word.one().lsh(0);
  public static final Word TRACE_ALLOC = Word.one().lsh(1);
  public static final Word TRACE_DEATH = Word.one().lsh(2);
  public static final Word TRACE_FIELD_SET = Word.one().lsh(3);
  public static final Word TRACE_ARRAY_SET = Word.one().lsh(4);
  public static final Word TRACE_TIB_SET = Word.one().lsh(5);
  public static final Word TRACE_STATIC_SET = Word.one().lsh(6);
  public static final Word TRACE_BOOTSTART = Word.one().lsh(7);
  public static final Word TRACE_BOOTEND = Word.one().lsh(8);
  public static final Word TRACE_GCSTART = Word.one().lsh(9);
  public static final Word TRACE_GCEND = Word.one().lsh(10);
  public static final Word TRACE_GCROOT = Word.one().lsh(11);
  public static final Word TRACE_GCBAR = Word.one().lsh(12);
  public static final Word TRACE_THREAD_SWITCH = Word.one().lsh(13);
  public static final Word TRACE_STACKDELTA = Word.one().lsh(14);
  public static final Word TRACE_ROOTPTR = Word.one().lsh(15);
  public static final Word TRACE_EXACT_IMMORTAL_ALLOC = Word.one().lsh(16);
  public static final Word TRACE_IMMORTAL_ALLOC = Word.one().lsh(17);

  private TracingConstants() {
    // prevent instantiation
  }

}
