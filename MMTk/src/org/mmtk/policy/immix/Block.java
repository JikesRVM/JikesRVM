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

import static org.mmtk.policy.immix.ImmixConstants.BLOCKS_IN_CHUNK;
import static org.mmtk.policy.immix.ImmixConstants.BLOCK_MASK;
import static org.mmtk.policy.immix.ImmixConstants.BYTES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.BYTES_IN_LINE;
import static org.mmtk.policy.immix.ImmixConstants.CHUNK_MASK;
import static org.mmtk.policy.immix.ImmixConstants.LINES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.LOG_BYTES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.LOG_BYTES_IN_LINE;
import static org.mmtk.policy.immix.ImmixConstants.MAX_BLOCK_MARK_STATE;
import static org.mmtk.policy.immix.ImmixConstants.SANITY_CHECK_LINE_MARKS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_CHECK_REUSE_EFFICIENCY;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DEFRAG_TO_IMMORTAL;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DEFRAG_WITHOUT_BUDGET;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EXACT_LINE_MARKS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_MARK_HISTO_SCALING;
import static org.mmtk.policy.immix.ImmixConstants.TMP_SUPPORT_DEFRAG;
import static org.mmtk.policy.immix.ImmixConstants.TMP_TRIAL_MARK_HISTO;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_BLOCK_COUNT_META;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_BLOCK_LIVE_BYTE_COUNTS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_CONSERVATIVE_BLOCK_MARKS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_LINE_MARKS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_VERBOSE_MARK_STATS;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;

@Uninterruptible
public class Block implements Constants {

  static Address align(Address ptr) {
    return ptr.toWord().and(BLOCK_MASK.not()).toAddress();
  }

  public static boolean isAligned(Address address) {
    return address.EQ(align(address));
  }

  private static int getChunkIndex(Address block) {
    return block.toWord().and(CHUNK_MASK).rshl(LOG_BYTES_IN_BLOCK).toInt();
  }

  /***************************************************************************
   * Block marking
   */
  public static boolean isUnused(Address address) {
    return getBlockMarkState(address) == UNALLOCATED_BLOCK_STATE;
  }

  static boolean isUnusedState(Address cursor) {
    return cursor.loadShort() == UNALLOCATED_BLOCK_STATE;
  }

  static boolean isUnmarkedState(Address cursor) {
    return cursor.loadShort() == UNMARKED_BLOCK_STATE;
  }

  static boolean isMarkedState(Address cursor) {
    return cursor.loadShort() == MARKED_BLOCK_STATE;
  }

  static void setToUnmarkedState(Address cursor) {
    cursor.store(UNMARKED_BLOCK_STATE);
  }

  static Address clearMarkStateAndAdvance(Address cursor) {
    short value = cursor.loadShort();
    if (value != Block.UNALLOCATED_BLOCK_STATE)
      cursor.store(Block.UNMARKED_BLOCK_STATE);
    return cursor.plus(BYTES_IN_BLOCK_STATE_ENTRY);
  }

  public static short getBlockMarkState(Address address) {
    return getBlockMarkStateAddress(address).loadShort();
  }

  static void markBlock(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!TMP_USE_LINE_MARKS);
    setBlockState(address, MARKED_BLOCK_STATE);
  }

  static void setBlockAsInUse(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isUnused(address));
    setBlockState(address, UNMARKED_BLOCK_STATE);
  }

  public static void setBlockAsReused(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!isUnused(address));
    setBlockState(address, REUSED_BLOCK_STATE);
  }

  static void setBlockAsUnallocated(Address address) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!isUnused(address));
    getBlockMarkStateAddress(address).store(UNALLOCATED_BLOCK_STATE);
  }

  private static void setBlockState(Address address, short value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(value != UNALLOCATED_BLOCK_STATE);
    getBlockMarkStateAddress(address).store(value);
  }

  static Address getBlockMarkStateAddress(Address address) {
    Address chunk = Chunk.align(address);
    int index = getChunkIndex(address);
    Address rtn = chunk.plus(Chunk.BLOCK_STATE_TABLE_OFFSET).plus(index<<LOG_BYTES_IN_BLOCK_STATE_ENTRY);
    if (VM.VERIFY_ASSERTIONS) {
      Address block = chunk.plus(index<<LOG_BYTES_IN_BLOCK);
      VM.assertions._assert(isAligned(block));
      boolean valid = rtn.GE(chunk.plus(Chunk.BLOCK_STATE_TABLE_OFFSET)) && rtn.LT(chunk.plus(Chunk.BLOCK_STATE_TABLE_OFFSET+BLOCK_STATE_TABLE_BYTES));
      VM.assertions._assert(valid);
    }
    return rtn;
  }

  /***************************************************************************
   * Sweeping
   */
  static short sweepOneBlock(Address block, int[] markHistogram, int[] availHistogram) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(block));

    final boolean unused = isUnused(block);
    if (unused && !SANITY_CHECK_LINE_MARKS)
      return 0;

    Address markTable = Line.getBlockMarkTable(block);
    short markCount = 0;
    short conservativeSpillCount = 0;
    byte mark, lastMark = 0;
    for (int l = 0; l < LINES_IN_BLOCK; l++) {
      Address markAddr = markTable.plus(Offset.fromIntSignExtend(l<<Line.LOG_BYTES_IN_LINE_MARK));
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(markAddr.GE(Chunk.align(block).plus(Chunk.LINE_MARK_TABLE_OFFSET)));
        VM.assertions._assert(markAddr.LT(Chunk.align(block).plus(Chunk.LINE_MARK_TABLE_OFFSET+Line.LINE_MARK_TABLE_BYTES)));
      }
      mark = markAddr.loadByte();

      if (mark != Line.LINE_UNMARKED_VALUE)
        markCount++;
      else if ((TMP_USE_CONSERVATIVE_BLOCK_MARKS || TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS) && lastMark != 0)
        conservativeSpillCount++;
      else if (SANITY_CHECK_LINE_MARKS && (TMP_EXACT_LINE_MARKS || lastMark == 0)) {
        if (TMP_CHECK_REUSE_EFFICIENCY) ImmixSpace.TMPreusableLineCount++;
        VM.memory.zero(block.plus(l<<LOG_BYTES_IN_LINE),Extent.fromIntZeroExtend(BYTES_IN_LINE));
      }

      lastMark = mark;
    }
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(markCount <= LINES_IN_BLOCK);
      VM.assertions._assert(markCount + conservativeSpillCount <= LINES_IN_BLOCK);
      VM.assertions._assert(markCount == 0 || !isUnused(block));
    }
    if (TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS) {
      short bucket = (short) (TMP_TRIAL_MARK_HISTO ? (markCount/TMP_MARK_HISTO_SCALING) : conservativeSpillCount);
      getDefragStateAddress(block).store(conservativeSpillCount);
      if (TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(markCount >= conservativeSpillCount);
        markHistogram[bucket] += markCount;
      }
      if (TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION) {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(markCount >= conservativeSpillCount);
        availHistogram[bucket] += (LINES_IN_BLOCK - (markCount + conservativeSpillCount));
      }
    }

    if (TMP_VERBOSE_MARK_STATS && markCount != 0) {
      ImmixSpace.bytesMarkedLines.inc((markCount + conservativeSpillCount)<<LOG_BYTES_IN_LINE);
      ImmixSpace.bytesConsvMarkedLines.inc(conservativeSpillCount<<LOG_BYTES_IN_LINE);
      ImmixSpace.bytesMarkedBlocks.inc(BYTES_IN_BLOCK);
    }

    if (TMP_USE_CONSERVATIVE_BLOCK_MARKS)
      markCount = (short) (markCount + conservativeSpillCount);

    if (TMP_CHECK_REUSE_EFFICIENCY) {
      if (markCount == 0) {
        ImmixSpace.TMPreusableLineCount -= LINES_IN_BLOCK;
      } else if (markCount <= ImmixSpace.getReusuableMarkStateThreshold(false)) {
        ImmixSpace.TMPreusableBlockCount++;
      }
    }
    return markCount;
  }


  /****************************************************************************
   * Per-block live byte count
   */
  static void incrementLiveCount(Address block, int value) {
    Address address = getLiveCountAddress(block);
    short old = address.loadShort();
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(old + value <= Short.MAX_VALUE);
    old += value;
    address.store(value);
  }

  private static Address getLiveCountAddress(Address address) {
    Address chunk = Chunk.align(address);
    int index = getChunkIndex(address);
    Address rtn = chunk.plus(Chunk.BLOCK_LIVE_BYTE_TABLE_OFFSET).plus(index);
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(TMP_USE_BLOCK_LIVE_BYTE_COUNTS);
      Address block = chunk.plus(index<<LOG_BYTES_IN_BLOCK);
      VM.assertions._assert(Block.isAligned(block));
      boolean valid = rtn.GE(chunk.plus(Chunk.BLOCK_LIVE_BYTE_TABLE_OFFSET)) && rtn.LT(chunk.plus(Chunk.BLOCK_LIVE_BYTE_TABLE_OFFSET+Block.BLOCK_LIVE_BYTE_TABLE_BYTES));
      VM.assertions._assert(valid);
    }
    return rtn;
  }

  /****************************************************************************
   * Block defrag state
   */
  public static boolean isDefragSource(Address address) {
    return getDefragStateAddress(address).loadShort() == BLOCK_IS_DEFRAG_SOURCE;
  }

  static void clearConservativeSpillCount(Address address) {
    getDefragStateAddress(address).store((short) 0);
  }

  static short getConservativeSpillCount(Address address) {
    return getDefragStateAddress(address).loadShort();
  }

  static Address getDefragStateAddress(Address address) {
    Address chunk = Chunk.align(address);
    int index = getChunkIndex(address);
    Address rtn = chunk.plus(Chunk.BLOCK_DEFRAG_STATE_TABLE_OFFSET).plus(index<<LOG_BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY);
    if (VM.VERIFY_ASSERTIONS) {
      Address block = chunk.plus(index<<LOG_BYTES_IN_BLOCK);
      VM.assertions._assert(isAligned(block));
      boolean valid = rtn.GE(chunk.plus(Chunk.BLOCK_DEFRAG_STATE_TABLE_OFFSET)) && rtn.LT(chunk.plus(Chunk.BLOCK_DEFRAG_STATE_TABLE_OFFSET+BLOCK_DEFRAG_STATE_TABLE_BYTES));
      VM.assertions._assert(valid);
    }
    return rtn;
  }

  static void resetLineMarksAndDefragStateTable(short threshold, Address markStateBase, Address defragStateBase,
      Address lineMarkBase, int block) {
    Offset csOffset = Offset.fromIntZeroExtend(block<<LOG_BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY);
    Offset msOffset = Offset.fromIntZeroExtend(block<<LOG_BYTES_IN_BLOCK_STATE_ENTRY);
    short state = (TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS && !TMP_TRIAL_MARK_HISTO) ? defragStateBase.loadShort(csOffset) : markStateBase.loadShort(msOffset);
    short defragState = BLOCK_IS_NOT_DEFRAG_SOURCE;
    if (TMP_DEFRAG_TO_IMMORTAL)
      defragState = BLOCK_IS_DEFRAG_SOURCE;
    else if (TMP_DEFRAG_WITHOUT_BUDGET) {
      if (state != UNALLOCATED_BLOCK_STATE) defragState = BLOCK_IS_DEFRAG_SOURCE;
    } else if (TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS) {
      if ((TMP_TRIAL_MARK_HISTO && state <= threshold && state != UNALLOCATED_BLOCK_STATE) || (!TMP_TRIAL_MARK_HISTO && state >= threshold)) defragState = BLOCK_IS_DEFRAG_SOURCE;
    } else {
      if (!TMP_DEFRAG_WITHOUT_BUDGET && state > threshold && state <= MAX_BLOCK_MARK_STATE) defragState = BLOCK_IS_DEFRAG_SOURCE;
    }
    defragStateBase.store(defragState, csOffset);
    if (defragState == BLOCK_IS_DEFRAG_SOURCE) {
      VM.memory.zero(lineMarkBase.plus(block<<Line.LOG_LINE_MARK_BYTES_PER_BLOCK), Extent.fromIntZeroExtend(Line.LINE_MARK_BYTES_PER_BLOCK));
    }
  }

  private static final short UNALLOCATED_BLOCK_STATE = 0;
  private static final short UNMARKED_BLOCK_STATE = MAX_BLOCK_MARK_STATE + 1;
  private static final short MARKED_BLOCK_STATE = MAX_BLOCK_MARK_STATE + 2;
  private static final short REUSED_BLOCK_STATE = MAX_BLOCK_MARK_STATE + 3;

  private static final short BLOCK_IS_NOT_DEFRAG_SOURCE = 0;
  private static final short BLOCK_IS_DEFRAG_SOURCE = 1;

  /* block states */
  static final int LOG_BYTES_IN_BLOCK_STATE_ENTRY = LOG_BYTES_IN_SHORT; // use a short for now
  static final int BYTES_IN_BLOCK_STATE_ENTRY = 1<<LOG_BYTES_IN_BLOCK_STATE_ENTRY;
  static final int BLOCK_STATE_TABLE_BYTES = BLOCKS_IN_CHUNK<<LOG_BYTES_IN_BLOCK_STATE_ENTRY;

  /* per-block live byte counts */
  static final int LOG_BYTES_IN_BLOCK_LIVE_COUNT_ENTRY = LOG_BYTES_IN_SHORT;
  static final int BLOCK_LIVE_BYTE_TABLE_BYTES = (TMP_USE_BLOCK_COUNT_META || TMP_USE_BLOCK_LIVE_BYTE_COUNTS) ? BLOCKS_IN_CHUNK<<LOG_BYTES_IN_BLOCK_LIVE_COUNT_ENTRY : 0;

  /* per-block defrag state */
  static final int LOG_BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY = LOG_BYTES_IN_SHORT;
  static final int BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY = 1<<LOG_BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY;

  static final int BLOCK_DEFRAG_STATE_TABLE_BYTES = TMP_SUPPORT_DEFRAG ? BLOCKS_IN_CHUNK<<LOG_BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY : 0;
}
