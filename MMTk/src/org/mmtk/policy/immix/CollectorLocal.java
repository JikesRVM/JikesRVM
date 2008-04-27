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

import static org.mmtk.policy.immix.ImmixConstants.DONT_CLEAR_MARKS_AT_EVERY_GC;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DEFRAG_TO_IMMORTAL;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DEFRAG_WITHOUT_BUDGET;
import static org.mmtk.policy.immix.ImmixConstants.TMP_SUPPORT_DEFRAG;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_BLOCK_LIVE_BYTE_COUNTS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_LINE_MARKS;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;

/**
 * This class implements unsynchronized (local) elements of an
 * immix collector.  Marking is done using both a bit in
 * each header's object word, and a mark byte.  Sweeping is
 * performed lazily.<p>
 *
 */
@Uninterruptible
public final class CollectorLocal implements Constants {

  /****************************************************************************
   *
   * Class variables
   */


  /****************************************************************************
   *
   * Instance variables
   */
  private final ImmixSpace immixSpace;
  private final ChunkList chunkMap;
  private final Defrag defrag;


  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param space The mark-sweep space to which this allocator
   * instances is bound.
   */
  public CollectorLocal(ImmixSpace space) {
    immixSpace = space;
    chunkMap = immixSpace.getChunkMap();
    defrag = immixSpace.getDefrag();
  }

  /****************************************************************************
   *
   * Collection
   */

  /**
   * Prepare for a collection. If paranoid, perform a sanity check.
   */
  public void prepare(boolean majorGC) {
    int ordinal = VM.collection.activeGCThreadOrdinal();
    if (ImmixConstants.DONT_CLEAR_MARKS_AT_EVERY_GC) {
      if (majorGC && !immixSpace.inImmixDefragCollection())
        clearAllBlockMarkState(ordinal);
    } else {
      if (majorGC) {
        if (!TMP_SUPPORT_DEFRAG || TMP_DEFRAG_TO_IMMORTAL || TMP_DEFRAG_WITHOUT_BUDGET || !immixSpace.inImmixDefragCollection())
          clearAllLineMarks(ordinal);
        else {
          short threshold = TMP_USE_CONSERVATIVE_SPILLS_FOR_DEFRAG_TARGETS ? Defrag.defragSpillThreshold : ImmixSpace.getReusuableMarkStateThreshold(true);
          resetLineMarksAndDefragStateTable(ordinal, threshold);
        }
      }
    }
  }

  private void resetLineMarksAndDefragStateTable(int ordinal, final short threshold) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!DONT_CLEAR_MARKS_AT_EVERY_GC && TMP_USE_LINE_MARKS && immixSpace.inImmixDefragCollection());
    int stride = VM.collection.activeGCThreads();
    Address chunk = chunkMap.firstChunk(ordinal, stride);
    while (!chunk.isZero()) {
      Chunk.resetLineMarksAndDefragStateTable(chunk, threshold);
      if (TMP_USE_BLOCK_LIVE_BYTE_COUNTS)
        VM.memory.zero(chunk.plus(Chunk.BLOCK_LIVE_BYTE_TABLE_OFFSET), Extent.fromIntZeroExtend(Block.BLOCK_LIVE_BYTE_TABLE_BYTES));
      chunk = chunkMap.nextChunk(chunk, ordinal, stride);
    }
  }

  private void clearAllLineMarks(int ordinal) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!DONT_CLEAR_MARKS_AT_EVERY_GC && TMP_USE_LINE_MARKS);
    int stride = VM.collection.activeGCThreads();
    Address chunk = chunkMap.firstChunk(ordinal, stride);
    while (!chunk.isZero()) {
      Chunk.clearLineMarks(chunk);
      chunk = chunkMap.nextChunk(chunk, ordinal, stride);
    }
  }

  private void clearAllBlockMarkState(int ordinal) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(DONT_CLEAR_MARKS_AT_EVERY_GC);
    int stride = VM.collection.activeGCThreads();
    Address chunk = chunkMap.firstChunk(ordinal, stride);
    while (!chunk.isZero()) {
      Chunk.clearBlockMarkState(chunk);
      chunk = chunkMap.nextChunk(chunk, ordinal, stride);
    }
  }

  /**
   * Finish up after a collection.
   *
   * We help sweeping all the blocks in parallel.
   */
  public void release(boolean majorGC) {
    sweepAllBlocks(majorGC);
  }

  private void sweepAllBlocks(boolean majorGC) {
    int stride = VM.collection.activeGCThreads();
    int ordinal = VM.collection.activeGCThreadOrdinal();
    Address chunk = chunkMap.firstChunk(ordinal, stride);
    while (!chunk.isZero()) {
      Chunk.sweep(chunk, Chunk.getHighWater(chunk), immixSpace, defrag.getSpillMarkHistogram(ordinal), defrag.getSpillAvailHistogram(ordinal));
      chunk = chunkMap.nextChunk(chunk, ordinal, stride);
    }
  }
}
