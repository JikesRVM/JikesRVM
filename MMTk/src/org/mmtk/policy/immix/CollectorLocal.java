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

import static org.mmtk.policy.immix.ImmixConstants.*;

import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;

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
    int ordinal = VM.activePlan.collector().parallelWorkerOrdinal();
    if (majorGC) {
      if (immixSpace.inImmixDefragCollection()) {
        short threshold = Defrag.defragSpillThreshold;
        resetLineMarksAndDefragStateTable(ordinal, threshold);
      }
    }
  }

  private void resetLineMarksAndDefragStateTable(int ordinal, final short threshold) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(immixSpace.inImmixDefragCollection());
    int stride = VM.activePlan.collector().parallelWorkerCount();
    Address chunk = chunkMap.firstChunk(ordinal, stride);
    while (!chunk.isZero()) {
      Chunk.resetLineMarksAndDefragStateTable(chunk, threshold);
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
    int stride = VM.activePlan.collector().parallelWorkerCount();
    int ordinal = VM.activePlan.collector().parallelWorkerOrdinal();
    int[] markSpillHisto = defrag.getAndZeroSpillMarkHistogram(ordinal);
    Address chunk = chunkMap.firstChunk(ordinal, stride);
    final byte markValue = immixSpace.lineMarkState;
    final boolean resetMarks = majorGC && markValue == MAX_LINE_MARK_STATE;
    while (!chunk.isZero()) {
      Chunk.sweep(chunk, Chunk.getHighWater(chunk), immixSpace, markSpillHisto, markValue, resetMarks);
      chunk = chunkMap.nextChunk(chunk, ordinal, stride);
    }
  }
}
