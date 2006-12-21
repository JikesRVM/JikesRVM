/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;
import org.mmtk.utility.heap.HeapGrowthManager;

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
  public static DebugAddress debugAddress;
  public static DummyEnum dummyEnum;
  public static EagerCompleteSweep eagerCompleteSweep;
  public static EagerMmapSpaces eagerMmapSpaces;
  public static FixedNursery fixedNursery;
  public static FragmentationStats fragmentationStats;
  public static FullHeapSystemGC fullHeapSystemGC;
  public static GCspyPort gcspyPort;
  public static GCspyTileSize gcspyTileSize;
  public static GCspyWait gcspyWait;
  public static GCTimeCap gcTimeCap;
  public static GenCycleDetection genCycleDetection;
  public static IgnoreSystemGC ignoreSystemGC;
  public static MarkSweepMarkBits markSweepMarkBits;
  public static MetaDataLimit metaDataLimit;
  public static NoFinalizer noFinalizer;
  public static NoReferenceTypes noReferenceTypes;
  public static NurserySize nurserySize;
  public static PrintPhaseStats printPhaseStats;
  public static ProtectOnRelease protectOnRelease;
  public static SanityCheck sanityCheck;
  public static StressFactor stressFactor;
  public static TraceRate traceRate;
  public static VariableSizeHeap variableSizeHeap;
  public static VerboseFragmentationStats verboseFragmentationStats;
  public static Verbose verbose;
  public static VerboseTiming verboseTiming;
  public static XmlStats xmlStats;
  
  /**
   * Print the options for the current run in XML format
   */
  public static void printOptionsXml() {
    Log.writeln("<options>");
    
    startOpt("minHeap"); 
    Log.write(HeapGrowthManager.getInitialHeapSize());
    units("bytes");
    endOpt();
    
    startOpt("maxHeap"); 
    Log.write(HeapGrowthManager.getMaxHeapSize()); 
    units("bytes");
    endOpt();
    
    Option opt = Option.getFirst();
    while (opt != null) {
      opt.log(Option.XML);
      opt = opt.getNext();
    }
    Log.writeln("</options>");
  }

  private static void startOpt(String key) {
    Log.write("<option name=\""); Log.write(key); Log.write("\" value=\"");
  }
  private static void units(String units) {
    Log.write("\" units=\""); Log.write(units);
  }
  private static void endOpt() {
    Log.writeln("\"/>");
  }
}
