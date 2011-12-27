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
package org.mmtk.policy.immix;

import static org.mmtk.policy.Space.BYTES_IN_CHUNK;
import static org.mmtk.policy.immix.ImmixConstants.*;

import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.Mmapper;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

@Uninterruptible
public class Chunk implements Constants {

  public static Address align(Address ptr) {
    return ptr.toWord().and(CHUNK_MASK.not()).toAddress();
  }

  static boolean isAligned(Address ptr) {
    return ptr.EQ(align(ptr));
  }

  static int getByteOffset(Address ptr) {
    return ptr.toWord().and(CHUNK_MASK).toInt();
  }

  /**
   * Return the number of pages of metadata required per chunk.
   */
  static int getRequiredMetaDataPages() {
    Extent bytes = Extent.fromIntZeroExtend(ROUNDED_METADATA_BYTES_PER_CHUNK);
    return Conversions.bytesToPagesUp(bytes);
  }

  static void sweep(Address chunk, Address end, ImmixSpace space, int[] markHistogram, final byte markValue, final boolean resetMarks) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    Address start = getFirstUsableBlock(chunk);
    Address cursor = Block.getBlockMarkStateAddress(start);
    for (int index = FIRST_USABLE_BLOCK_INDEX; index < BLOCKS_IN_CHUNK; index++) {
      Address block = chunk.plus(index<<LOG_BYTES_IN_BLOCK);
      if (block.GT(end)) break;
      final boolean defragSource = space.inImmixDefragCollection() && Block.isDefragSource(block);
      short marked = Block.sweepOneBlock(block, markHistogram, markValue, resetMarks);
      if (marked == 0) {
        if (!Block.isUnusedState(cursor)) {
          space.release(block);
          if (defragSource) Defrag.defragBytesFreed.inc(BYTES_IN_BLOCK);
        }
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Block.isUnused(block));
      } else {
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(marked > 0 && marked <= LINES_IN_BLOCK);
        Block.setState(cursor, marked);
        if (defragSource) Defrag.defragBytesNotFreed.inc(BYTES_IN_BLOCK);
      }
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(Block.isUnused(block) || (Block.getBlockMarkState(block) == marked && marked > 0 && marked <= MAX_BLOCK_MARK_STATE));
      cursor = cursor.plus(Block.BYTES_IN_BLOCK_STATE_ENTRY);
    }
  }

  static void clearMetaData(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(isAligned(chunk));
      VM.assertions._assert(Conversions.isPageAligned(chunk));
      VM.assertions._assert(Conversions.isPageAligned(ROUNDED_METADATA_BYTES_PER_CHUNK));
    }
    Mmapper.ensureMapped(chunk, ROUNDED_METADATA_PAGES_PER_CHUNK);
    VM.memory.zero(false, chunk, Extent.fromIntZeroExtend(ROUNDED_METADATA_BYTES_PER_CHUNK));
    if (VM.VERIFY_ASSERTIONS) checkMetaDataCleared(chunk, chunk);
  }

  private static void checkMetaDataCleared(Address chunk, Address value) {
    VM.assertions._assert(isAligned(chunk));
    Address block = Chunk.getHighWater(chunk);
    if (value.EQ(chunk)) {
      VM.assertions._assert(block.isZero());
      block = chunk.plus(Chunk.ROUNDED_METADATA_BYTES_PER_CHUNK);
    } else {
      block = block.plus(BYTES_IN_BLOCK); // start at first block after highwater
      VM.assertions._assert(Block.align(block).EQ(block));
    }
    while (block.LT(chunk.plus(BYTES_IN_CHUNK))) {
      VM.assertions._assert(Chunk.align(block).EQ(chunk));
      VM.assertions._assert(Block.isUnused(block));
      block = block.plus(BYTES_IN_BLOCK);
    }
  }

  static void updateHighWater(Address value) {
    Address chunk = align(value);
    if (getHighWater(chunk).LT(value)) {
      setHighWater(chunk, value);
    }
  }

  private static void setHighWater(Address chunk, Address value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    chunk.plus(HIGHWATER_OFFSET).store(value);
  }

  public static Address getHighWater(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    return chunk.plus(HIGHWATER_OFFSET).loadAddress();
  }

  static void setMap(Address chunk, int value) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    chunk.plus(MAP_OFFSET).store(value);
  }

  static int getMap(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    int rtn = chunk.plus(MAP_OFFSET).loadInt();
    return (rtn < 0) ? -rtn : rtn;
  }

  static void resetLineMarksAndDefragStateTable(Address chunk, short threshold) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    Address markStateBase = Block.getBlockMarkStateAddress(chunk);
    Address defragStateBase = Block.getDefragStateAddress(chunk);
    Address lineMarkBase = Line.getChunkMarkTable(chunk);
    for (int b = FIRST_USABLE_BLOCK_INDEX; b < BLOCKS_IN_CHUNK; b++) {
      Block.resetLineMarksAndDefragStateTable(threshold, markStateBase, defragStateBase, lineMarkBase, b);
    }
  }

  static Address getFirstUsableBlock(Address chunk) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(isAligned(chunk));
    Address rtn = chunk.plus(ROUNDED_METADATA_BYTES_PER_CHUNK);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(rtn.EQ(Block.align(rtn)));
    return rtn;
  }

  private static final int LOG_BYTES_IN_HIGHWATER_ENTRY = LOG_BYTES_IN_ADDRESS;
  private static final int HIGHWATER_BYTES = 1<<LOG_BYTES_IN_HIGHWATER_ENTRY;
  private static final int LOG_BYTES_IN_MAP_ENTRY = LOG_BYTES_IN_INT;
  private static final int MAP_BYTES = 1<<LOG_BYTES_IN_MAP_ENTRY;

  /* byte offsets for each type of metadata */
  static final int LINE_MARK_TABLE_OFFSET = 0;
  static final int BLOCK_STATE_TABLE_OFFSET = LINE_MARK_TABLE_OFFSET + Line.LINE_MARK_TABLE_BYTES;
  static final int BLOCK_DEFRAG_STATE_TABLE_OFFSET = BLOCK_STATE_TABLE_OFFSET + Block.BLOCK_STATE_TABLE_BYTES;
  static final int HIGHWATER_OFFSET = BLOCK_DEFRAG_STATE_TABLE_OFFSET + Block.BLOCK_DEFRAG_STATE_TABLE_BYTES;
  static final int MAP_OFFSET = HIGHWATER_OFFSET + HIGHWATER_BYTES;
  static final int METADATA_BYTES_PER_CHUNK = MAP_OFFSET + MAP_BYTES;

  /* FIXME we round the metadata up to block sizes just to ensure the underlying allocator gives us aligned requests */
  private static final int BLOCK_MASK = (1<<LOG_BYTES_IN_BLOCK) - 1;
  static final int ROUNDED_METADATA_BYTES_PER_CHUNK = (METADATA_BYTES_PER_CHUNK + BLOCK_MASK) & ~BLOCK_MASK;
  static final int ROUNDED_METADATA_PAGES_PER_CHUNK = ROUNDED_METADATA_BYTES_PER_CHUNK>>LOG_BYTES_IN_PAGE;
  public static final int FIRST_USABLE_BLOCK_INDEX = ROUNDED_METADATA_BYTES_PER_CHUNK>>LOG_BYTES_IN_BLOCK;
}
