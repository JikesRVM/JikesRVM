/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.policy.immix;

import static org.mmtk.policy.Space.BYTES_IN_CHUNK;
import static org.mmtk.policy.Space.LOG_BYTES_IN_CHUNK;
import static org.mmtk.utility.Constants.LOG_BYTES_IN_PAGE;

import org.mmtk.plan.Plan;
import org.mmtk.vm.VM;
import org.vmmagic.unboxed.Word;

public class ImmixConstants {
  public static final boolean BUILD_FOR_STICKYIMMIX = Plan.NEEDS_LOG_BIT_IN_HEADER;

  /* start temporary experimental constants --- should not be allowed to lurk longer than necessary */
  public static final int TMP_MIN_SPILL_THRESHOLD = 2;
  public static final boolean TMP_PREFER_COPY_ON_NURSERY_GC = true;
  /* end temporary experimental constants */

  public static final boolean MARK_LINE_AT_SCAN_TIME = true; // else do it at mark time
  static final boolean DONT_CLEAR_MARKS_AT_EVERY_GC = false && Plan.NEEDS_LOG_BIT_IN_HEADER;

  public static final boolean SANITY_CHECK_LINE_MARKS = false && VM.VERIFY_ASSERTIONS;

  public static final float DEFAULT_LINE_REUSE_RATIO = (float) 0.99;
  public static final float DEFAULT_DEFRAG_LINE_REUSE_RATIO = (float) 0.99;
  public static final float DEFAULT_SIMPLE_SPILL_THRESHOLD = (float) 0.25;
  public static final int DEFAULT_DEFRAG_HEADROOM = 0; // number of pages.
  public static final float DEFAULT_DEFRAG_HEADROOM_FRACTION = (float) 0.020;
  public static final int DEFAULT_DEFRAG_FREE_HEADROOM = 0; // number of pages.  This should only deviate from zero for analytical purposes.  Otherwise the defragmenter is cheating!
  public static final float DEFAULT_DEFRAG_FREE_HEADROOM_FRACTION = (float) 0.0;
  /* sizes etc */
  static final int LOG_BYTES_IN_BLOCK = (LOG_BYTES_IN_PAGE > 15 ? LOG_BYTES_IN_PAGE : 15);
  public static final int BYTES_IN_BLOCK = 1<<LOG_BYTES_IN_BLOCK;
  static final int LOG_PAGES_IN_BLOCK = LOG_BYTES_IN_BLOCK - LOG_BYTES_IN_PAGE;
  static final int PAGES_IN_BLOCK = 1<<LOG_PAGES_IN_BLOCK;
  static final int LOG_BLOCKS_IN_CHUNK = LOG_BYTES_IN_CHUNK-LOG_BYTES_IN_BLOCK;
  static final int BLOCKS_IN_CHUNK = 1<<LOG_BLOCKS_IN_CHUNK;

  public static final int LOG_BYTES_IN_LINE = 8;
  static final int LOG_LINES_IN_BLOCK = LOG_BYTES_IN_BLOCK - LOG_BYTES_IN_LINE;
  public static final short LINES_IN_BLOCK = (short) (1<<LOG_LINES_IN_BLOCK);
  static final int LOG_LINES_IN_CHUNK = LOG_BYTES_IN_CHUNK - LOG_BYTES_IN_LINE;
  static final int LINES_IN_CHUNK = 1<<LOG_LINES_IN_CHUNK;

  public static final int BYTES_IN_LINE = 1<<LOG_BYTES_IN_LINE;

  private static final int LOG_BLOCKS_IN_RECYCLE_ALLOC_CHUNK = 4; // 3 + 15 -> 19 (512KB)
  private static final int LOG_BYTES_IN_RECYCLE_ALLOC_CHUNK = LOG_BLOCKS_IN_RECYCLE_ALLOC_CHUNK + LOG_BYTES_IN_BLOCK;
  static final int BYTES_IN_RECYCLE_ALLOC_CHUNK = 1<<LOG_BYTES_IN_RECYCLE_ALLOC_CHUNK;

  public static final short MAX_BLOCK_MARK_STATE = LINES_IN_BLOCK;
         static final short MAX_CONSV_SPILL_COUNT = (short) (LINES_IN_BLOCK/2);
  public static final short SPILL_HISTOGRAM_BUCKETS = (short) (MAX_CONSV_SPILL_COUNT + 1);
  public static final short MARK_HISTOGRAM_BUCKETS = (short) (LINES_IN_BLOCK + 1);
         static final short MAX_COLLECTORS = 16; // nothing special here---we can increase this at the cost of a few hundred bites at build time.

  public static final Word RECYCLE_ALLOC_CHUNK_MASK = Word.fromIntZeroExtend(BYTES_IN_RECYCLE_ALLOC_CHUNK - 1);
  protected static final Word CHUNK_MASK = Word.fromIntZeroExtend(BYTES_IN_CHUNK - 1);
  public static final Word BLOCK_MASK = Word.fromIntZeroExtend(BYTES_IN_BLOCK - 1);
  protected static final Word LINE_MASK = Word.fromIntZeroExtend(BYTES_IN_LINE - 1);

}
