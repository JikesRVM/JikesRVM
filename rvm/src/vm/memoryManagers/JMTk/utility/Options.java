/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

/*
 * Options including heap sizes.
 *
 * @author Perry Cheng
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class Options implements VM_Uninterruptible, Constants {

  private static int initialHeapSize; 
  private static int maxHeapSize;     
  private static int currentHeapSize; 
  private static double growThreshold = 0.8;  // grow heap when live ratio exceeds this
  private static double growRatio     = 1.2;  // grow heap size by this ratio

  static int minNurseryPages  = Plan.DEFAULT_MIN_NURSERY;
  static int maxNurseryPages  = Plan.DEFAULT_MAX_NURSERY;
  static int metaDataPages    = 128;  // perform GC if metadata >= 512K
  static int cycleMetaDataPages = MAX_INT;  // default to no cycle m/data limit
  static int cycleDetectionPages = 256;   // do c/d if < 1MB remaining
  static int gcTimeCap        = MAX_INT;  // default to no time cap
  static int stressTest       = MAX_INT;  // default to never
  public static boolean ignoreSystemGC = false;
  public static boolean genCycleDetection = false;
  public static boolean fragmentationStats = false;
  public static boolean verboseFragmentationStats = false;
  public static boolean verboseTiming = false;

  public static boolean noFinalizer = false;
  public static boolean noReferenceTypes = false;

  public static int getCurrentHeapSize() {
    return currentHeapSize;
  }

  public static void setDefaultHeapSizes(int initial, int max) {
    initialHeapSize = initial;
    max = maxHeapSize;
    if (initialHeapSize > maxHeapSize) 
      maxHeapSize = initialHeapSize;
    // VM.sysWriteln("The boot record's heap sizes are inconsistent due to a faulty configuration file.");
  }

  public static boolean updateCurrentHeapSize(int usedHeapSize) {
    double liveRatio = ((double) usedHeapSize) / currentHeapSize;
    if (liveRatio > growThreshold) {
      int newSize = (int) (growRatio * currentHeapSize);
      if (newSize > 10 * (1<<20)) {
	newSize = (newSize + (1<<20)) >> 20 << 20;
      }
      else 
	newSize = (newSize + (1<<10)) >> 10 << 10;
      if (newSize > maxHeapSize) newSize = maxHeapSize;
      if (newSize > currentHeapSize) {
	currentHeapSize = newSize;
	return true;
      }
    }
    return false;
  }

  public static void process (String arg) throws VM_PragmaInterruptible {
    if (arg.startsWith("noFinalizer")) {
	VM_Interface.sysWriteln("NO MORE FINALIZER!");
	noFinalizer = true;
    }
    else if (arg.equals("noReferenceTypes")) {
      VM_Interface.sysWriteln("NO MORE REFERENCE TYPE PROCESSING!");
      noReferenceTypes = true;
    }
    else if (arg.equals("ignoreSystemGC")) {
      ignoreSystemGC = true;
    }
    else if (arg.equals("fragmentationStats")) {
      fragmentationStats = true;
    }
    else if (arg.equals("verboseFragmentationStats")) {
      fragmentationStats = true;
      verboseFragmentationStats = true;
    }
    else if (arg.equals("verboseTiming")) {
      verboseTiming = true;
    }
    else if (arg.startsWith("initial=")) {
      String tmp = arg.substring(8);
      int size = Integer.parseInt(tmp);
      if (size <= 0) VM_Interface.sysFail("Unreasonable heap size " + tmp);
      initialHeapSize = size * (1 << 20);
    }
    else if (arg.startsWith("max=")) {
      String tmp = arg.substring(4);
      int size = Integer.parseInt(tmp);
      if (size <= 0) VM_Interface.sysFail("Unreasonable heap size " + tmp);
      maxHeapSize = size * (1 << 20);
    }
    else if (arg.startsWith("growThreshold=")) {
      String tmp = arg.substring(14);
      double gt = Double.parseDouble(tmp);
      if (gt <= 0.0 || gt >= 1.0) VM_Interface.sysFail("Unreasonable growThreshold " + tmp);
      growThreshold = gt;
    }
    else if (arg.startsWith("growRatio=")) {
      String tmp = arg.substring(10);
      double gr = Double.parseDouble(tmp);
      if (gr < 1.0) VM_Interface.sysFail("Unreasonable growRatio " + tmp);
      growRatio = gr;
    }
    else if (arg.startsWith("verbose=")) {
      String tmp = arg.substring(8);
      int level = Integer.parseInt(tmp);
      if (level < 0) VM_Interface.sysFail("Unreasonable verbosity level " + tmp);
      Plan.verbose = level;
    }
    else if(arg.startsWith("fixedNursery=") ||
	    arg.startsWith("nursery_size=")) {
      String tmp = arg.substring(13);
      maxNurseryPages = Conversions.bytesToPagesUp(Integer.parseInt(tmp)<<20);
      minNurseryPages = maxNurseryPages;
      if (maxNurseryPages <= 0) VM_Interface.sysFail("Unreasonable nursery size " + tmp);
    }
    else if (arg.startsWith("boundedNursery=")) {
      String tmp = arg.substring(15);
      maxNurseryPages = Conversions.bytesToPagesUp(Integer.parseInt(tmp)<<20);
      if (maxNurseryPages <= 0) VM_Interface.sysFail("Unreasonable nursery size " + tmp);
    }
    else if (arg.startsWith("metadata_limit=")) {
      String tmp = arg.substring(15);
      metaDataPages = Conversions.bytesToPagesUp(Integer.parseInt(tmp)<<10);
      if (metaDataPages <= 0) VM_Interface.sysFail("Unreasonable metadata limit " + tmp);
    }
    else if (arg.startsWith("cycle_metadata_limit=")) {
      String tmp = arg.substring(21);
      cycleMetaDataPages = Conversions.bytesToPagesUp(Integer.parseInt(tmp)<<10);
      if (cycleMetaDataPages <= 0) VM_Interface.sysFail("Unreasonable cycle metadata limit " + tmp);
    }
    else if (arg.startsWith("cycle_detection_limit=")) {
      String tmp = arg.substring(22);
      cycleDetectionPages = Conversions.bytesToPagesUp(Integer.parseInt(tmp)<<10);
      if (cycleDetectionPages <= 0) VM_Interface.sysFail("Unreasonable cycle detection limit " + tmp);
    }
    else if (arg.startsWith("time_cap=")) {
      String tmp = arg.substring(9);
      gcTimeCap = Integer.parseInt(tmp);
      if (gcTimeCap <= 0) VM_Interface.sysFail("Unreasonable time cap " + tmp);
    }
    else if (arg.equals("gen_cycle_detection")) {
      genCycleDetection = true;
    }
    else if (arg.startsWith("stress=")) {
      String tmp = arg.substring(7);
      stressTest = 1<<Integer.parseInt(tmp);
    }
    else 
      VM_Interface.sysWriteln("Ignoring unknown GC option: ",arg);

    if (maxHeapSize < initialHeapSize) maxHeapSize = initialHeapSize;
    if (VM_Extent.fromInt(maxHeapSize).GT(Plan.MAX_SIZE)) {
	VM_Interface.sysWriteln("Specified heap size ",maxHeapSize >>> 20);
	VM_Interface.sysWriteln(" MB is greater than maximum supported heap size for this collector which is ",(int) (Plan.MAX_SIZE.toInt() >>> 20)," Mb");
	VM_Interface.sysFail("Max heap too large");
    }
    currentHeapSize = initialHeapSize;
  }

}
