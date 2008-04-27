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
import static org.mmtk.policy.immix.ImmixConstants.BYTES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.LINES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.LOG_BYTES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.LOG_BYTES_IN_LINE;
import static org.mmtk.policy.immix.ImmixConstants.MAX_BLOCK_MARK_STATE;
import static org.mmtk.policy.immix.ImmixConstants.MAX_COLLECTORS;
import static org.mmtk.policy.immix.ImmixConstants.MAX_CONSV_SPILL_COUNT;
import static org.mmtk.policy.immix.ImmixConstants.PAGES_IN_BLOCK;
import static org.mmtk.policy.immix.ImmixConstants.SPILL_HISTOGRAM_BUCKETS;
import static org.mmtk.policy.immix.ImmixConstants.TMP_ALWAYS_DEFRAG_ON_FULL_HEAP;
import static org.mmtk.policy.immix.ImmixConstants.TMP_CONSERVATIVE_DEFRAG_TRIGGER;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DECREASE_AVAIL_WHEN_CALC_THRESHOLD;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DEFRAG_TO_IMMORTAL;
import static org.mmtk.policy.immix.ImmixConstants.TMP_DEFRAG_WITHOUT_BUDGET;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION;
import static org.mmtk.policy.immix.ImmixConstants.TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION;
import static org.mmtk.policy.immix.ImmixConstants.TMP_HEADROOM_FRACTION_WRT_TOTAL_HEAP;
import static org.mmtk.policy.immix.ImmixConstants.TMP_MARK_HISTO_SCALING;
import static org.mmtk.policy.immix.ImmixConstants.TMP_MIN_SPILL_THRESHOLD;
import static org.mmtk.policy.immix.ImmixConstants.TMP_SUPPORT_DEFRAG;
import static org.mmtk.policy.immix.ImmixConstants.TMP_TRIAL_MARK_HISTO;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_LIVE_COUNTS_FOR_SPILL_HISTOGRAM;
import static org.mmtk.policy.immix.ImmixConstants.TMP_USE_NAIVE_SPILL_DEFRAG_THRESHOLD;
import static org.mmtk.policy.immix.ImmixConstants.TMP_VERBOSE_DEFRAG_STATS;

import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.options.DefragFreeHeadroom;
import org.mmtk.utility.options.DefragFreeHeadroomFraction;
import org.mmtk.utility.options.DefragHeadroom;
import org.mmtk.utility.options.DefragHeadroomFraction;
import org.mmtk.utility.options.DefragLineReuseRatio;
import org.mmtk.utility.options.DefragSimpleSpillThreshold;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.EventCounter;
import org.mmtk.utility.statistics.SizeCounter;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

@Uninterruptible
public class Defrag  implements Constants {


  private int defragHeadroomPages = 0;
  private int defragFreeHeadroomPages = 0;
  private boolean inDefragCollection = false;
  private int debugBytesDefraged = 0;
  private int availableCleanPagesForDefrag;
  private boolean defragSpaceExhausted = true;
  private int[] spillMarkHistogram = TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION ? null : new int[SPILL_HISTOGRAM_BUCKETS];
  private int[][] spillMarkHistograms = TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION ? new int[MAX_COLLECTORS][SPILL_HISTOGRAM_BUCKETS] : null;
  private int[] spillAvailHistogram = TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION ? null : new int[SPILL_HISTOGRAM_BUCKETS];
  private int[][] spillAvailHistograms = TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION ? new int[MAX_COLLECTORS][SPILL_HISTOGRAM_BUCKETS] : null;
  public static SizeCounter defragBytesAvailable = TMP_VERBOSE_DEFRAG_STATS ? new SizeCounter("bytesAvail") : null;
  public static SizeCounter defragBytesSkipped = TMP_VERBOSE_DEFRAG_STATS ? new SizeCounter("bytesSkipped") : null;
  public static SizeCounter defragBytesCopied = TMP_VERBOSE_DEFRAG_STATS ? new SizeCounter("bytesCopied") : null;
  public static SizeCounter defragCleanBytesUsed = TMP_SUPPORT_DEFRAG ? new SizeCounter("cleanUsed") : null;
  /* verbose stats (used only on stats runs since they induce overhead when gathred) */
  public static SizeCounter defragBytesNotFreed = TMP_SUPPORT_DEFRAG ? new SizeCounter("bytesNotFreed") : null;
  public static SizeCounter defragBytesFreed = TMP_SUPPORT_DEFRAG ? new SizeCounter("bytesFreed") : null;
  public static SizeCounter defragCleanBytesAvailable = TMP_SUPPORT_DEFRAG ? new SizeCounter("cleanAvail") : null;

  private final FreeListPageResource pr;
  private boolean debugCollectionTypeDetermined = false;
  static short defragSpillThreshold = 0;
  static short defragReusableMarkStateThreshold = 0;
  public static EventCounter defrags = TMP_SUPPORT_DEFRAG ? new EventCounter("defrags") : null;

  static {
    Options.defragLineReuseRatio = new DefragLineReuseRatio();
    Options.defragHeadroom = new DefragHeadroom();
    Options.defragHeadroomFraction = new DefragHeadroomFraction();
    Options.defragFreeHeadroom = new DefragFreeHeadroom();
    Options.defragFreeHeadroomFraction = new DefragFreeHeadroomFraction();
    Options.defragSimpleSpillThreshold = new DefragSimpleSpillThreshold();
    defragReusableMarkStateThreshold = (short) (Options.defragLineReuseRatio.getValue() * MAX_BLOCK_MARK_STATE);
  }

  Defrag(FreeListPageResource pr) {
    this.pr = pr;
  }

  boolean inDefrag() { return inDefragCollection; }

  void prepare(ChunkList chunkMap, ImmixSpace space) {
    if (defragHeadroomPages > 0)
      pr.unconditionallyReleasePages(defragHeadroomPages);

    availableCleanPagesForDefrag = TMP_DEFRAG_WITHOUT_BUDGET ? VM.activePlan.global().getTotalPages() : (VM.activePlan.global().getTotalPages() - VM.activePlan.global().getPagesReserved());
    if (availableCleanPagesForDefrag < 0) availableCleanPagesForDefrag = 0;
    defragSpaceExhausted = false;
    availableCleanPagesForDefrag += defragFreeHeadroomPages;
    if (inDefragCollection) {
      chunkMap.consolidateMap();
      establishDefragSpillThreshold(chunkMap, space);
      defrags.inc();
      defragCleanBytesAvailable.inc(availableCleanPagesForDefrag<<LOG_BYTES_IN_PAGE);
      if (TMP_VERBOSE_DEFRAG_STATS) defragBytesAvailable.inc(availableCleanPagesForDefrag<<LOG_BYTES_IN_PAGE);
    }
  }

  void globalRelease() {
    if (TMP_VERBOSE_DEFRAG_STATS && inDefragCollection && Options.verbose.getValue() >= 1) {
      Log.write("[ avil: "); defragBytesAvailable.printCurrentVolume();
      Log.write(", cavl: "); defragCleanBytesAvailable.printCurrentVolume();
      Log.write(", cuse: "); defragCleanBytesUsed.printCurrentVolume();
      Log.write(", copy: "); defragBytesCopied.printCurrentVolume();
      Log.write(", skip: "); defragBytesSkipped.printCurrentVolume();
      Log.write(", free: "); defragBytesFreed.printCurrentVolume();
      Log.write(", held: "); defragBytesNotFreed.printCurrentVolume();
      Log.write("]"); Log.flush();
    }

    if (Options.defragHeadroom.getPages() > 0)
      defragHeadroomPages = Options.defragHeadroom.getPages();
    else if (Options.defragHeadroomFraction.getValue() > 0)
      defragHeadroomPages = (int) ((TMP_HEADROOM_FRACTION_WRT_TOTAL_HEAP ? VM.activePlan.global().getTotalPages() : pr.reservedPages()) * Options.defragHeadroomFraction.getValue());
    else
      defragHeadroomPages = 0;
    if (Options.defragFreeHeadroom.getPages() > 0)
      defragFreeHeadroomPages = Options.defragFreeHeadroom.getPages();
    else if (Options.defragFreeHeadroomFraction.getValue() > 0)
      defragFreeHeadroomPages = (int) ((TMP_HEADROOM_FRACTION_WRT_TOTAL_HEAP ? VM.activePlan.global().getTotalPages() : pr.reservedPages()) * Options.defragFreeHeadroomFraction.getValue());
    else
      defragFreeHeadroomPages = 0;

    if (defragHeadroomPages > 0)
      pr.unconditionallyReservePages(defragHeadroomPages);

    inDefragCollection = false;
    debugCollectionTypeDetermined = false;
  }

  void setCollectionKind(boolean emergencyCollection, boolean collectWholeHeap, int collectionAttempt, int requiredAtStart, boolean userTriggered, boolean exhaustedReusableSpace) {
    inDefragCollection = TMP_SUPPORT_DEFRAG && collectWholeHeap && (TMP_ALWAYS_DEFRAG_ON_FULL_HEAP || userTriggered || emergencyCollection || (!TMP_CONSERVATIVE_DEFRAG_TRIGGER && !exhaustedReusableSpace));
    if (inDefragCollection) {
      debugBytesDefraged = 0;
    }
    debugCollectionTypeDetermined = true;
  }

  boolean determined(boolean inDefrag) { return debugCollectionTypeDetermined && !(inDefrag ^ inDefragCollection); }

  void getBlock() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!TMP_DEFRAG_TO_IMMORTAL && (!inDefragCollection || !defragSpaceExhausted));
    if (!TMP_DEFRAG_TO_IMMORTAL && !TMP_DEFRAG_WITHOUT_BUDGET && availableCleanPagesForDefrag <= 0)
      defragSpaceExhausted = true;
    availableCleanPagesForDefrag -= PAGES_IN_BLOCK;
    debugBytesDefraged += BYTES_IN_BLOCK;
    Defrag.defragCleanBytesUsed.inc(BYTES_IN_BLOCK);
  }

  private void establishDefragSpillThreshold(ChunkList chunkMap, ImmixSpace space) {
    int availableLines;
    if (TMP_VERBOSE_DEFRAG_STATS || !TMP_USE_NAIVE_SPILL_DEFRAG_THRESHOLD) {
      availableLines = space.getAvailableLines(spillAvailHistogram);
      if (TMP_VERBOSE_DEFRAG_STATS) {
        Log.write("[");
        Log.write(availableLines); Log.write(" ");
        Log.write(availableLines<<LOG_BYTES_IN_LINE); Log.writeln("]");
        Defrag.defragBytesAvailable.inc(availableLines<<LOG_BYTES_IN_LINE);
      }
    }

    if (TMP_USE_NAIVE_SPILL_DEFRAG_THRESHOLD)
      defragSpillThreshold =  (short) (Options.defragSimpleSpillThreshold.getValue() * MAX_CONSV_SPILL_COUNT);
    else {
      if (!TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION)
        updateMarkHistogram(chunkMap);
      availableLines += availableCleanPagesForDefrag<<(LOG_BYTES_IN_PAGE - LOG_BYTES_IN_LINE);
      int requiredLines = 0;
      short threshold = (short) (TMP_TRIAL_MARK_HISTO ? 0 : MAX_CONSV_SPILL_COUNT);
      int limit = (int) (availableLines / Options.defragLineReuseRatio.getValue());
      int collectors = VM.activePlan.collectorCount();
      for (short index = MAX_CONSV_SPILL_COUNT; index >= TMP_MIN_SPILL_THRESHOLD && limit > requiredLines; index--) {
        threshold = (short) (TMP_TRIAL_MARK_HISTO ? MAX_CONSV_SPILL_COUNT - index : index);
        int thisBucketMark = 0;
        int thisBucketAvail = 0;
        if (TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION)
          for (int c = 0; c < collectors; c++) thisBucketMark += spillMarkHistograms[c][threshold];
        else
          thisBucketMark = spillMarkHistogram[threshold];
        if (TMP_DECREASE_AVAIL_WHEN_CALC_THRESHOLD) {
          if (TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION)
            for (int c = 0; c < collectors; c++) thisBucketAvail += spillAvailHistograms[c][threshold];
          else
            thisBucketAvail = spillAvailHistogram[threshold];
        }
        if (TMP_VERBOSE_DEFRAG_STATS && Options.verbose.getValue() >= 1) {
          Log.write("spill histo<");
          Log.write(threshold); Log.write(" ");
          Log.write(thisBucketMark); Log.write(" ");
          Log.write(thisBucketAvail); Log.write(" ");
          Log.write(requiredLines);Log.write(" ");
          Log.write(availableLines); Log.write(" ");
          Log.write(limit); Log.writeln(">");
        }
        if (TMP_DECREASE_AVAIL_WHEN_CALC_THRESHOLD)
          limit -= thisBucketAvail;
        requiredLines += thisBucketMark;
      }
      defragSpillThreshold = threshold;
    }
  }


  private void updateMarkHistogram(ChunkList chunkMap) {
    for (int bkt = 0; bkt < SPILL_HISTOGRAM_BUCKETS; bkt++)
      spillMarkHistogram[bkt] = 0;
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(!TMP_USE_LIVE_COUNTS_FOR_SPILL_HISTOGRAM);
      /* we're creating an approximate histogram.  for now leverage the fact that max mark states are no more than 22% different to lines in block */
      VM.assertions._assert(MAX_BLOCK_MARK_STATE < (1.02*LINES_IN_BLOCK));
    }
    Address chunk = chunkMap.getHeadChunk();
    Address end = chunk;
    while (!chunk.isZero()) {
      Address markStateCursor = Block.getBlockMarkStateAddress(Chunk.getFirstUsableBlock(chunk));
      Address spillCountCursor = Block.getDefragStateAddress(Chunk.getFirstUsableBlock(chunk));
      for (int block = Chunk.FIRST_USABLE_BLOCK_INDEX; block < BLOCKS_IN_CHUNK; block++) {
        short markState = markStateCursor.loadShort();
        if (markState > 0) {
          short spillState = spillCountCursor.loadShort();
          if (VM.VERIFY_ASSERTIONS) {
            VM.assertions._assert(markStateCursor.EQ(Block.getBlockMarkStateAddress(chunk.plus(block<<LOG_BYTES_IN_BLOCK))));
            VM.assertions._assert(spillCountCursor.EQ(Block.getDefragStateAddress(chunk.plus(block<<LOG_BYTES_IN_BLOCK))));
            VM.assertions._assert(markStateCursor.GE(chunk.plus(Chunk.BLOCK_STATE_TABLE_OFFSET)));
            VM.assertions._assert(markStateCursor.LT(chunk.plus(Chunk.BLOCK_STATE_TABLE_OFFSET+Block.BLOCK_STATE_TABLE_BYTES)));
            VM.assertions._assert(spillCountCursor.GE(chunk.plus(Chunk.BLOCK_DEFRAG_STATE_TABLE_OFFSET)));
            VM.assertions._assert(spillCountCursor.LT(chunk.plus(Chunk.BLOCK_DEFRAG_STATE_TABLE_OFFSET+Block.BLOCK_DEFRAG_STATE_TABLE_BYTES)));
            VM.assertions._assert(spillState >= 0 && spillState <= MAX_CONSV_SPILL_COUNT);
          }
          short bucket = (short) (TMP_TRIAL_MARK_HISTO ? (short) markState/TMP_MARK_HISTO_SCALING : spillState);
          if (TMP_TRIAL_MARK_HISTO && bucket >= SPILL_HISTOGRAM_BUCKETS) bucket = SPILL_HISTOGRAM_BUCKETS - 1;
          spillMarkHistogram[bucket] += (markState - spillState); // FIXME!
        }
        markStateCursor = markStateCursor.plus(Block.BYTES_IN_BLOCK_STATE_ENTRY);
        spillCountCursor = spillCountCursor.plus(Block.BYTES_IN_BLOCK_DEFRAG_STATE_ENTRY);
      }
      chunk = chunkMap.nextChunk(chunk, end);
    }
  }
  boolean spaceExhausted() { return defragSpaceExhausted; }

  // FIXME are the histograms zeroed?  What about non-participating threads?
  int[] getSpillMarkHistogram(int ordinal) {
    int[] rtn = null;
    if (TMP_EAGER_SPILL_MARK_HISTO_CONSTRUCTION) {
      rtn = spillMarkHistograms[ordinal];
        for (int i = 0; i <= MAX_CONSV_SPILL_COUNT; i++)
        rtn[i] = 0;
    }
    return rtn;
  }
  int[] getSpillAvailHistogram(int ordinal) { return TMP_EAGER_SPILL_AVAIL_HISTO_CONSTRUCTION ? spillAvailHistograms[ordinal] : null; }
}
