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

import static org.mmtk.policy.immix.ImmixConstants.*;


import org.mmtk.utility.Constants;
import org.mmtk.utility.Log;
import org.mmtk.utility.heap.FreeListPageResource;
import org.mmtk.utility.options.DefragFreeHeadroom;
import org.mmtk.utility.options.DefragFreeHeadroomFraction;
import org.mmtk.utility.options.DefragHeadroom;
import org.mmtk.utility.options.DefragHeadroomFraction;
import org.mmtk.utility.options.DefragLineReuseRatio;
import org.mmtk.utility.options.DefragSimpleSpillThreshold;
import org.mmtk.utility.options.DefragStress;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.statistics.EventCounter;
import org.mmtk.utility.statistics.SizeCounter;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class Defrag  implements Constants {


  private int defragHeadroomPages = 0;
  private int defragFreeHeadroomPages = 0;
  private boolean inDefragCollection = false;
  private int debugBytesDefraged = 0;
  private int availableCleanPagesForDefrag;
  private boolean defragSpaceExhausted = true;
  private int[][] spillMarkHistograms = new int[MAX_COLLECTORS][SPILL_HISTOGRAM_BUCKETS];
  private int[] spillAvailHistogram = new int[SPILL_HISTOGRAM_BUCKETS];
  public static SizeCounter defragCleanBytesUsed = new SizeCounter("cleanUsed");
  /* verbose stats (used only on stats runs since they induce overhead when gathred) */
  public static SizeCounter defragBytesNotFreed = new SizeCounter("bytesNotFreed");
  public static SizeCounter defragBytesFreed = new SizeCounter("bytesFreed");
  public static SizeCounter defragCleanBytesAvailable = new SizeCounter("cleanAvail");

  private final FreeListPageResource pr;
  private boolean debugCollectionTypeDetermined = false;
  static short defragSpillThreshold = 0;
  static short defragReusableMarkStateThreshold = 0;
  public static EventCounter defrags = new EventCounter("defrags");

  static {
    Options.defragLineReuseRatio = new DefragLineReuseRatio();
    Options.defragHeadroom = new DefragHeadroom();
    Options.defragHeadroomFraction = new DefragHeadroomFraction();
    Options.defragFreeHeadroom = new DefragFreeHeadroom();
    Options.defragFreeHeadroomFraction = new DefragFreeHeadroomFraction();
    Options.defragSimpleSpillThreshold = new DefragSimpleSpillThreshold();
    Options.defragStress = new DefragStress();
    defragReusableMarkStateThreshold = (short) (Options.defragLineReuseRatio.getValue() * MAX_BLOCK_MARK_STATE);
  }

  Defrag(FreeListPageResource pr) {
    this.pr = pr;
  }

  boolean inDefrag() { return inDefragCollection; }

  void prepare(ChunkList chunkMap, ImmixSpace space) {
    if (defragHeadroomPages > 0)
      pr.unconditionallyReleasePages(defragHeadroomPages);

    availableCleanPagesForDefrag = VM.activePlan.global().getTotalPages() - VM.activePlan.global().getPagesReserved();
    if (availableCleanPagesForDefrag < 0) availableCleanPagesForDefrag = 0;
    defragSpaceExhausted = false;
    availableCleanPagesForDefrag += defragFreeHeadroomPages;
    if (inDefragCollection) {
      if (Options.verbose.getValue() > 0) {
        Log.write("[Defrag]");
      }
      chunkMap.consolidateMap();
      establishDefragSpillThreshold(chunkMap, space);
      defrags.inc();
      defragCleanBytesAvailable.inc(availableCleanPagesForDefrag<<LOG_BYTES_IN_PAGE);
    }
    availableCleanPagesForDefrag += VM.activePlan.global().getCollectionReserve();
  }

  void globalRelease() {
    if (Options.defragHeadroom.getPages() > 0)
      defragHeadroomPages = Options.defragHeadroom.getPages();
    else if (Options.defragHeadroomFraction.getValue() > 0)
      defragHeadroomPages = (int) (pr.reservedPages() * Options.defragHeadroomFraction.getValue());
    else
      defragHeadroomPages = 0;
    if (Options.defragFreeHeadroom.getPages() > 0)
      defragFreeHeadroomPages = Options.defragFreeHeadroom.getPages();
    else if (Options.defragFreeHeadroomFraction.getValue() > 0)
      defragFreeHeadroomPages = (int) (pr.reservedPages() * Options.defragFreeHeadroomFraction.getValue());
    else
      defragFreeHeadroomPages = 0;

    if (defragHeadroomPages > 0)
      pr.unconditionallyReservePages(defragHeadroomPages);

    if (Options.verbose.getValue() > 2) {
      Log.write("(Defrag summary: cu: "); defragCleanBytesUsed.printCurrentVolume();
      Log.write(" nf: "); defragBytesNotFreed.printCurrentVolume();
      Log.write(" fr: "); defragBytesFreed.printCurrentVolume();
      Log.write(" av: "); defragCleanBytesAvailable.printCurrentVolume();
      Log.write(")");
    }

    inDefragCollection = false;
    debugCollectionTypeDetermined = false;
  }

  void setCollectionKind(boolean emergencyCollection, boolean collectWholeHeap, int collectionAttempt, int requiredAtStart, boolean userTriggered, boolean exhaustedReusableSpace) {
    inDefragCollection =  collectWholeHeap && (Options.defragStress.getValue() || userTriggered || emergencyCollection); // || (!BUILD_FOR_STICKYIMMIX && !exhaustedReusableSpace));
    if (inDefragCollection) {
      debugBytesDefraged = 0;
    }
    debugCollectionTypeDetermined = true;
  }

  boolean determined(boolean inDefrag) { return debugCollectionTypeDetermined && !(inDefrag ^ inDefragCollection); }

  void getBlock() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!inDefragCollection || !defragSpaceExhausted);
    if (availableCleanPagesForDefrag <= 0)
      defragSpaceExhausted = true;
    availableCleanPagesForDefrag -= PAGES_IN_BLOCK;
    debugBytesDefraged += BYTES_IN_BLOCK;
    Defrag.defragCleanBytesUsed.inc(BYTES_IN_BLOCK);
  }

  private void establishDefragSpillThreshold(ChunkList chunkMap, ImmixSpace space) {
    int cleanLines = space.getAvailableLines(spillAvailHistogram);
    int availableLines = cleanLines + availableCleanPagesForDefrag<<(LOG_BYTES_IN_PAGE - LOG_BYTES_IN_LINE);

    int requiredLines = 0;
    short threshold = (short) MAX_CONSV_SPILL_COUNT;
    int limit = (int) (availableLines / Options.defragLineReuseRatio.getValue());
    if (VM.VERIFY_ASSERTIONS && Options.verbose.getValue() > 2) {
      Log.write("[threshold: "); Log.write("cl: "); Log.write(cleanLines);
      Log.write(" al: "); Log.write(availableLines);
      Log.write(" lm: "); Log.write(limit);
    }
    int collectors = VM.activePlan.collectorCount();
    for (short index = MAX_CONSV_SPILL_COUNT; index >= TMP_MIN_SPILL_THRESHOLD && limit > requiredLines; index--) {
      threshold = (short) index;
      int thisBucketMark = 0;
      int thisBucketAvail = 0;
      for (int c = 0; c < collectors; c++) thisBucketMark += spillMarkHistograms[c][threshold];

      thisBucketAvail = spillAvailHistogram[threshold];
      limit -= thisBucketAvail;
      requiredLines += thisBucketMark;
      if (VM.VERIFY_ASSERTIONS && Options.verbose.getValue() > 2) {
        Log.write(" ("); Log.write(index); Log.write(" "); Log.write(limit); Log.write(","); Log.write(requiredLines); Log.write(")");
      }
    }
    if (VM.VERIFY_ASSERTIONS && Options.verbose.getValue() > 2) {
      Log.write(" threshold: "); Log.write(threshold); Log.write("]");
    }
    defragSpillThreshold = threshold;
  }


  boolean spaceExhausted() { return defragSpaceExhausted; }

  int[] getAndZeroSpillMarkHistogram(int ordinal) {
    int[] rtn = spillMarkHistograms[ordinal];
    for (int i = 0; i < SPILL_HISTOGRAM_BUCKETS; i++)
      rtn[i] = 0;
    return rtn;
  }
}
