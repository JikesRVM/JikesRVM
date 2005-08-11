/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.utility.options;

/**
 * Repository for all option instances.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public final class Options {
  /* Options system options */
  public static EchoOptions echoOptions = new EchoOptions();

  /* Other options */
  public static BoundedNursery boundedNursery;
  public static CycleFilterThreshold cycleFilterThreshold;
  public static CycleMetaDataLimit cycleMetaDataLimit;
  public static CycleTriggerThreshold cycleTriggerThreshold;
  public static DummyEnum dummyEnum;
  public static FixedNursery fixedNursery;
  public static FragmentationStats fragmentationStats;
  public static FullHeapSystemGC fullHeapSystemGC;
  public static GCspyPort gcspyPort;
  public static GCspyTileSize gcspyTileSize;
  public static GCspyWait gcspyWait;
  public static GCTimeCap gcTimeCap;
  public static GenCycleDetection genCycleDetection;
  public static IgnoreSystemGC ignoreSystemGC;
  public static MetaDataLimit metaDataLimit;
  public static NoFinalizer noFinalizer;
  public static NoReferenceTypes noReferenceTypes;
  public static NurserySize nurserySize;
  public static PrintPhaseStats printPhaseStats;
  public static ProtectOnRelease protectOnRelease;
  public static StressFactor stressFactor;
  public static TraceRate traceRate;
  public static VariableSizeHeap variableSizeHeap;
  public static VerboseFragmentationStats verboseFragmentationStats;
  public static Verbose verbose;
  public static VerboseTiming verboseTiming;
}
