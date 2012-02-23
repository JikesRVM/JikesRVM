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
package org.mmtk.utility.options;

import org.vmutil.options.OptionSet;

/**
 * Repository for all option instances.
 */
public final class Options {
  public static OptionSet set;

  /* Other options */
  public static ConcurrentTrigger concurrentTrigger;
  public static CycleFilterThreshold cycleFilterThreshold;
  public static CycleMetaDataLimit cycleMetaDataLimit;
  public static CycleTriggerThreshold cycleTriggerThreshold;
  public static DebugAddress debugAddress;
  public static NurseryZeroing nurseryZeroing;
  public static DummyEnum dummyEnum;
  public static DefragHeadroom defragHeadroom;
  public static DefragHeadroomFraction defragHeadroomFraction;
  public static DefragFreeHeadroom defragFreeHeadroom;
  public static DefragFreeHeadroomFraction defragFreeHeadroomFraction;
  public static DefragLineReuseRatio defragLineReuseRatio;
  public static DefragSimpleSpillThreshold defragSimpleSpillThreshold;
  public static DefragStress defragStress;
  public static EagerCompleteSweep eagerCompleteSweep;
  public static EagerMmapSpaces eagerMmapSpaces;
  public static FragmentationStats fragmentationStats;
  public static FullHeapSystemGC fullHeapSystemGC;
  public static GCspyPort gcspyPort;
  public static GCspyTileSize gcspyTileSize;
  public static GCspyWait gcspyWait;
  public static GCTimeCap gcTimeCap;
  public static GenCycleDetection genCycleDetection;
  public static HarnessAll harnessAll;
  public static IgnoreSystemGC ignoreSystemGC;
  public static LineReuseRatio lineReuseRatio;
  public static MarkSweepMarkBits markSweepMarkBits;
  public static MetaDataLimit metaDataLimit;
  public static NoFinalizer noFinalizer;
  public static NoReferenceTypes noReferenceTypes;
  public static NurserySize nurserySize;
  public static PerfEvents perfEvents;
  public static PretenureThresholdFraction pretenureThresholdFraction;
  public static PrintPhaseStats printPhaseStats;
  public static ProtectOnRelease protectOnRelease;
  public static SanityCheck sanityCheck;
  public static StressFactor stressFactor;
  public static Threads threads;
  public static TraceRate traceRate;
  public static VariableSizeHeap variableSizeHeap;
  public static VerboseFragmentationStats verboseFragmentationStats;
  public static Verbose verbose;
  public static VerboseTiming verboseTiming;
  public static XmlStats xmlStats;
}
