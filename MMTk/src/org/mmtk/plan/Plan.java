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
package org.mmtk.plan;

import org.mmtk.policy.MarkSweepSpace;
import org.mmtk.policy.Space;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.alloc.LinearScan;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.heap.Map;
import org.mmtk.utility.heap.VMRequest;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.mmtk.utility.sanitychecker.SanityChecker;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.VM;
import org.mmtk.vm.Collection;


import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements the global core functionality for all
 * memory management schemes.  All global MMTk plans should inherit from
 * this class.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities, and divides global and local state
 * into separate class hierarchies.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  There is a single instance of Plan (or the
 * appropriate sub-class), and a 1:1 mapping of PlanLocal to "kernel
 * threads" (aka CPUs).  Thus instance
 * methods of PlanLocal allow fast, unsynchronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance properties of MMTk plans.
 */
@Uninterruptible
public abstract class Plan implements Constants {
  /****************************************************************************
   * Constants
   */

  /* GC State */
  public static final int NOT_IN_GC = 0; // this must be zero for C code
  public static final int GC_PREPARE = 1; // before setup and obtaining root
  public static final int GC_PROPER = 2;

  /* Polling */
  public static final int DEFAULT_POLL_FREQUENCY = (128 << 10) >> LOG_BYTES_IN_PAGE;
  public static final int META_DATA_POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  /* Space Size Constants. */
  public static final boolean USE_CODE_SPACE = true;
  public static final float PLOS_FRAC = 0.07f;
  public static final int HEAP_FULL_MINIMUM = (1 << 17) >> LOG_BYTES_IN_PAGE; // 128K
  public static final int HEAP_FULL_PERCENTAGE = 2;

  /* Allocator Constants */
  public static final int ALLOC_DEFAULT = 0;
  public static final int ALLOC_NON_REFERENCE = 1;
  public static final int ALLOC_NON_MOVING = 2;
  public static final int ALLOC_IMMORTAL = 3;
  public static final int ALLOC_LOS = 4;
  public static final int ALLOC_PRIMITIVE_LOS = 5;
  public static final int ALLOC_GCSPY = 6;
  public static final int ALLOC_CODE = 7;
  public static final int ALLOC_LARGE_CODE = 8;
  public static final int ALLOC_HOT_CODE = USE_CODE_SPACE ? ALLOC_CODE : ALLOC_DEFAULT;
  public static final int ALLOC_COLD_CODE = USE_CODE_SPACE ? ALLOC_CODE : ALLOC_DEFAULT;
  public static final int ALLOC_STACK = ALLOC_LOS;
  public static final int ALLOCATORS = 9;
  public static final int DEFAULT_SITE = -1;

  /* Miscellaneous Constants */
//  public static final int LOS_SIZE_THRESHOLD = SegregatedFreeListSpace.MAX_CELL_SIZE;
  public static final int NON_PARTICIPANT = 0;
  public static final boolean GATHER_WRITE_BARRIER_STATS = false;
  public static final int DEFAULT_MIN_NURSERY = (256 * 1024) >> LOG_BYTES_IN_PAGE;
  public static final int DEFAULT_MAX_NURSERY = (32 << 20) >> LOG_BYTES_IN_PAGE;
  public static final boolean SCAN_BOOT_IMAGE = true;  // scan it for roots rather than trace it
  public static final int MAX_COLLECTION_ATTEMPTS = 10;
 // public static final boolean REQUIRES_LOS = VM.activePlan.constraints().requiresLOS();
  public static final int MAX_NON_LOS_DEFAULT_ALLOC_BYTES = VM.activePlan.constraints().maxNonLOSDefaultAllocBytes();
  public static final int MAX_NON_LOS_NONMOVING_ALLOC_BYTES = VM.activePlan.constraints().maxNonLOSNonMovingAllocBytes();
  public static final int MAX_NON_LOS_COPY_BYTES = VM.activePlan.constraints().maxNonLOSCopyBytes();


/* Do we support a log bit in the object header?  Some write barriers may use it */
  public static final boolean NEEDS_LOG_BIT_IN_HEADER = VM.activePlan.constraints().needsLogBitInHeader();

  /****************************************************************************
   * Class variables
   */

  /** The space that holds any VM specific objects (e.g. a boot image) */
  public static final Space vmSpace = VM.memory.getVMSpace();

  /** Any immortal objects allocated after booting are allocated here. */
  public static final ImmortalSpace immortalSpace = new ImmortalSpace("immortal", DEFAULT_POLL_FREQUENCY, VMRequest.create());

  /** All meta data that is used by MMTk is allocated (and accounted for) in the meta data space. */
  public static final RawPageSpace metaDataSpace = new RawPageSpace("meta", DEFAULT_POLL_FREQUENCY, VMRequest.create());

  /** Large objects are allocated into a special large object space. */
  public static final LargeObjectSpace loSpace = new LargeObjectSpace("los", DEFAULT_POLL_FREQUENCY, VMRequest.create());

  /** Space used by the sanity checker (used at runtime only if sanity checking enabled */
  public static final RawPageSpace sanitySpace = new RawPageSpace("sanity", Integer.MAX_VALUE, VMRequest.create());

  /** Space used to allocate objects that cannot be moved. we do not need a large space as the LOS is non-moving. */
  public static final MarkSweepSpace nonMovingSpace = new MarkSweepSpace("non-moving", DEFAULT_POLL_FREQUENCY, VMRequest.create());

  public static final MarkSweepSpace smallCodeSpace = USE_CODE_SPACE ? new MarkSweepSpace("sm-code", DEFAULT_POLL_FREQUENCY, VMRequest.create()) : null;
  public static final LargeObjectSpace largeCodeSpace = USE_CODE_SPACE ? new LargeObjectSpace("lg-code", DEFAULT_POLL_FREQUENCY, VMRequest.create()) : null;

  /* Space descriptors */
  public static final int IMMORTAL = immortalSpace.getDescriptor();
  public static final int VM_SPACE = vmSpace.getDescriptor();
  public static final int META = metaDataSpace.getDescriptor();
  public static final int LOS = loSpace.getDescriptor();
  public static final int SANITY = sanitySpace.getDescriptor();
  public static final int NON_MOVING = nonMovingSpace.getDescriptor();
  public static final int SMALL_CODE = USE_CODE_SPACE ? smallCodeSpace.getDescriptor() : 0;
  public static final int LARGE_CODE = USE_CODE_SPACE ? largeCodeSpace.getDescriptor() : 0;

  /** Timer that counts total time */
  public static final Timer totalTime = new Timer("time");

  /** Support for time-limited GCs */
  protected static long timeCap;

  /** Support for allocation-site identification */
  protected static int allocationSiteCount = 0;

  /** Global sanity checking state **/
  public static final SanityChecker sanityChecker = new SanityChecker();

  /****************************************************************************
   * Constructor.
   */
  public Plan() {
    /* Create base option instances */
    Options.verbose = new Verbose();
    Options.verboseTiming = new VerboseTiming();
    Options.stressFactor = new StressFactor();
    Options.noFinalizer = new NoFinalizer();
    Options.noReferenceTypes = new NoReferenceTypes();
    Options.fullHeapSystemGC = new FullHeapSystemGC();
    Options.harnessAll = new HarnessAll();
    Options.ignoreSystemGC = new IgnoreSystemGC();
    Options.metaDataLimit = new MetaDataLimit();
    Options.nurserySize = new NurserySize();
    Options.variableSizeHeap = new VariableSizeHeap();
    Options.eagerMmapSpaces = new EagerMmapSpaces();
    Options.sanityCheck = new SanityCheck();
    Options.debugAddress = new DebugAddress();
    Options.perfEvents = new PerfEvents();
    Map.finalizeStaticSpaceMap();
    registerSpecializedMethods();
  }

  /****************************************************************************
   * Boot.
   */

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  @Interruptible
  public void boot() {
  }

  /**
   * The postBoot method is called by the runtime immediately after
   * command-line arguments are available.  Note that allocation must
   * be supported prior to this point because the runtime
   * infrastructure may require allocation in order to parse the
   * command line arguments.  For this reason all plans should operate
   * gracefully on the default minimum heap size until the point that
   * boot is called.
   */
  @Interruptible
  public void postBoot() {
    VM.statistics.perfEventInit(Options.perfEvents.getValue());
    if (Options.verbose.getValue() > 2) Space.printVMMap();
    if (Options.verbose.getValue() > 3) VM.config.printConfig();
    if (Options.verbose.getValue() > 0) Stats.startAll();
    if (Options.eagerMmapSpaces.getValue()) Space.eagerlyMmapMMTkSpaces();
  }

  /**
   * The fullyBooted method is called by the runtime just before normal
   * execution commences.
   */
  @Interruptible
  public void fullyBooted() {
    initialized = true;
    if (Options.harnessAll.getValue()) harnessBegin();
  }

  /**
   * The VM is about to exit. Perform any clean up operations.
   *
   * @param value The exit value
   */
  @Interruptible
  public void notifyExit(int value) {
    if (Options.harnessAll.getValue()) harnessEnd();
    if (Options.verbose.getValue() == 1) {
      Log.write("[End ");
      totalTime.printTotalSecs();
      Log.writeln(" s]");
    } else if (Options.verbose.getValue() == 2) {
      Log.write("[End ");
      totalTime.printTotalMillis();
      Log.writeln(" ms]");
    }
    if (Options.verboseTiming.getValue()) printDetailedTiming(true);
  }

  /**
   * Any Plan can override this to provide additional plan specific
   * timing information.
   *
   * @param totals Print totals
   */
  protected void printDetailedTiming(boolean totals) {}

  /**
   * Perform any required write barrier action when installing an object reference
   * a boot time.
   *
   * @param reference the reference value that is to be stored
   * @return The raw value to be
   */
  public Word bootTimeWriteBarrier(Word reference) {
    return reference;
  }

  /****************************************************************************
   * Allocation
   */
  public static int getAllocationSite(boolean compileTime) {
    if (compileTime) // a new allocation site is being compiled
      return allocationSiteCount++;
    else             // an anonymous site
      return DEFAULT_SITE;
  }

  /****************************************************************************
   * Collection.
   */

  /**
   * Perform a (global) collection phase.
   */
  public abstract void collectionPhase(short phase);

  /**
   * Replace a phase.
   *
   * @param oldScheduledPhase The scheduled phase to insert after
   * @param scheduledPhase The scheduled phase to insert
   */
  @Interruptible
  public void replacePhase(int oldScheduledPhase, int scheduledPhase) {
    VM.assertions.fail("replacePhase not implemented for this plan");
  }


  /**
   * Insert a phase.
   *
   * @param markerScheduledPhase The scheduled phase to insert after
   * @param scheduledPhase The scheduled phase to insert
   */
  @Interruptible
  public void insertPhaseAfter(int markerScheduledPhase, int scheduledPhase) {
    short tempPhase = Phase.createComplex("auto-gen", null, markerScheduledPhase, scheduledPhase);
    replacePhase(markerScheduledPhase, Phase.scheduleComplex(tempPhase));
  }

  /**
   * @return Whether last GC was an exhaustive attempt to collect the heap.  For many collectors this is the same as asking whether the last GC was a full heap collection.
   */
  public boolean lastCollectionWasExhaustive() {
    return lastCollectionFullHeap();
  }

  /**
   * @return Whether last GC is a full GC.
   */
  public boolean lastCollectionFullHeap() {
    return true;
  }

  /**
   * @return Is last GC a full collection?
   */
  public static boolean isEmergencyCollection() {
    return emergencyCollection;
  }

  /**
   * @return True if we have run out of heap space.
   */
  public final boolean lastCollectionFailed() {
    return !(collectionTrigger == Collection.EXTERNAL_GC_TRIGGER ||
             collectionTrigger == Collection.INTERNAL_PHASE_GC_TRIGGER) &&
      (getPagesAvail() < getHeapFullThreshold() || getPagesAvail() < requiredAtStart);
  }

  /**
   * Force the next collection to be full heap.
   */
  public void forceFullHeapCollection() {}

  /**
   * @return Is current GC only collecting objects allocated since last GC.
   */
  public boolean isCurrentGCNursery() {
    return false;
  }

  private long lastStressPages = 0;

  /**
   * Return the expected reference count. For non-reference counting
   * collectors this becomes a true/false relationship.
   *
   * @param object The object to check.
   * @param sanityRootRC The number of root references to the object.
   * @return The expected (root excluded) reference count.
   */
  public int sanityExpectedRC(ObjectReference object, int sanityRootRC) {
    Space space = Space.getSpaceForObject(object);
    return space.isReachable(object) ? SanityChecker.ALIVE : SanityChecker.DEAD;
  }

  /**
   * Perform a linear scan of all spaces to check for possible leaks.
   * This is only called after a full-heap GC.
   *
   * @param scanner The scanner callback to use.
   */
  public void sanityLinearScan(LinearScan scanner) {
  }

  /**
   * @return True is a stress test GC is required
   */
  @Inline
  public final boolean stressTestGCRequired() {
    long pages = Space.cumulativeCommittedPages();
    if (initialized &&
        ((pages ^ lastStressPages) > Options.stressFactor.getPages())) {
      lastStressPages = pages;
      return true;
    } else
      return false;
  }

  /****************************************************************************
   * GC State
   */

  protected static int requiredAtStart;
  protected static int collectionTrigger;
  protected static boolean emergencyCollection;
  protected static boolean awaitingAsyncCollection;
  protected static boolean stacksPrepared;

  private static boolean initialized = false;
  private static boolean collectionTriggered;
  @Entrypoint
  private static int gcStatus = NOT_IN_GC; // shared variable

  /** @return Is the memory management system initialized? */
  public static boolean isInitialized() {
    return initialized;
  }

  /**
   * Has collection has triggered?
   */
  public static boolean isCollectionTriggered() {
    return collectionTriggered;
  }

  /**
   * Set that a collection has been triggered.
   */
  public static void setCollectionTriggered() {
    collectionTriggered = true;
  }

  /**
   * A collection has fully completed.  Clear the triggered flag.
   */
  public static void collectionComplete() {
    collectionTriggered = false;
  }

  /**
   * Return true if stacks have been prepared in this collection cycle.
   *
   * @return True if stacks have been prepared in this collection cycle.
   */
  public static boolean stacksPrepared() {
    return stacksPrepared;
  }
  /**
   * Return true if a collection is in progress.
   *
   * @return True if a collection is in progress.
   */
  public static boolean gcInProgress() {
    return gcStatus != NOT_IN_GC;
  }

  /**
   * Return true if a collection is in progress and past the preparatory stage.
   *
   * @return True if a collection is in progress and past the preparatory stage.
   */
  public static boolean gcInProgressProper() {
    return gcStatus == GC_PROPER;
  }

  /**
   * Sets the GC status.
   *
   * @param s The new GC status.
   */
  public static void setGCStatus(int s) {
    if (gcStatus == NOT_IN_GC) {
      /* From NOT_IN_GC to any phase */
      stacksPrepared = false;
      if (Stats.gatheringStats()) {
        Stats.startGC();
        VM.activePlan.global().printPreStats();
      }
    }
    VM.memory.isync();
    gcStatus = s;
    VM.memory.sync();
    if (gcStatus == NOT_IN_GC) {
      /* From any phase to NOT_IN_GC */
      if (Stats.gatheringStats()) {
        Stats.endGC();
        VM.activePlan.global().printPostStats();
      }
    }
  }

  /**
   * Print out statistics at the start of a GC
   */
  public void printPreStats() {
    if ((Options.verbose.getValue() == 1) ||
        (Options.verbose.getValue() == 2)) {
      Log.write("[GC "); Log.write(Stats.gcCount());
      if (Options.verbose.getValue() == 1) {
        Log.write(" Start ");
        Plan.totalTime.printTotalSecs();
        Log.write(" s");
      } else {
        Log.write(" Start ");
        Plan.totalTime.printTotalMillis();
        Log.write(" ms");
      }
      Log.write("   ");
      Log.write(Conversions.pagesToKBytes(getPagesUsed()));
      Log.write("KB ");
      Log.flush();
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("Collection "); Log.write(Stats.gcCount());
      Log.write(":        ");
      printUsedPages();
      Log.write("  Before Collection: ");
      Space.printUsageMB();
      if (Options.verbose.getValue() >= 4) {
        Log.write("                     ");
        Space.printUsagePages();
      }
      if (Options.verbose.getValue() >= 5) {
        Space.printVMMap();
      }
    }
  }

  /**
   * Print out statistics at the end of a GC
   */
  public final void printPostStats() {
    if ((Options.verbose.getValue() == 1) ||
        (Options.verbose.getValue() == 2)) {
      Log.write("-> ");
      Log.writeDec(Conversions.pagesToBytes(getPagesUsed()).toWord().rshl(10));
      Log.write("KB   ");
      if (Options.verbose.getValue() == 1) {
        totalTime.printLast();
        Log.writeln(" ms]");
      } else {
        Log.write("End ");
        totalTime.printTotal();
        Log.writeln(" ms]");
      }
    }
    if (Options.verbose.getValue() > 2) {
      Log.write("   After Collection: ");
      Space.printUsageMB();
      if (Options.verbose.getValue() >= 4) {
        Log.write("                     ");
        Space.printUsagePages();
      }
      if (Options.verbose.getValue() >= 5) {
        Space.printVMMap();
      }
      Log.write("                     ");
      printUsedPages();
      Log.write("    Collection time: ");
      totalTime.printLast();
      Log.writeln(" ms");
    }
  }

  public final void printUsedPages() {
    Log.write("reserved = ");
    Log.write(Conversions.pagesToMBytes(getPagesReserved()));
    Log.write(" MB (");
    Log.write(getPagesReserved());
    Log.write(" pgs)");
    Log.write("      total = ");
    Log.write(Conversions.pagesToMBytes(getTotalPages()));
    Log.write(" MB (");
    Log.write(getTotalPages());
    Log.write(" pgs)");
    Log.writeln();
  }

  /**
   * Set the collection trigger.
   */
  public static void setCollectionTrigger(int trigger) {
    collectionTrigger = trigger;
  }

  /****************************************************************************
   * Harness
   */
  protected static boolean insideHarness = false;

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions prior to the commencement of a
   * benchmark, such as a full heap collection, turning on
   * instrumentation, etc.  By default we do a full heap GC,
   * and then start stats collection.
   */
  @Interruptible
  public static void harnessBegin() {
    // Save old values.
    boolean oldFullHeap = Options.fullHeapSystemGC.getValue();
    boolean oldIgnore = Options.ignoreSystemGC.getValue();

    // Set desired values.
    Options.fullHeapSystemGC.setValue(true);
    Options.ignoreSystemGC.setValue(false);

    // Trigger a full heap GC.
    System.gc();

    // Restore old values.
    Options.ignoreSystemGC.setValue(oldIgnore);
    Options.fullHeapSystemGC.setValue(oldFullHeap);

    // Start statistics
    insideHarness = true;
    Stats.startAll();
  }

  /**
   * Generic hook to allow benchmarks to be harnessed.  A plan may use
   * this to perform certain actions after the completion of a
   * benchmark, such as a full heap collection, turning off
   * instrumentation, etc.  By default we stop all statistics objects
   * and print their values.
   */
  @Interruptible
  public static void harnessEnd()  {
    Stats.stopAll();
    insideHarness = false;
  }

  /****************************************************************************
   * VM Accounting
   */

  /* Global accounting and static access */

  /**
   * Return the amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).  Note that this may overstate the amount
   * of <i>available memory</i>, which must account for unused memory
   * that is held in reserve for copying, and therefore unavailable
   * for allocation.
   *
   * @return The amount of <i>free memory</i>, in bytes (where free is
   * defined as not in use).
   */
  public static Extent freeMemory() {
    return totalMemory().minus(usedMemory());
  }

  /**
   * Return the amount of <i>available memory</i>, in bytes.  Note
   * that this accounts for unused memory that is held in reserve
   * for copying, and therefore unavailable for allocation.
   *
   * @return The amount of <i>available memory</i>, in bytes.
   */
  public static Extent availableMemory() {
    return totalMemory().minus(reservedMemory());
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this excludes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static Extent usedMemory() {
    return Conversions.pagesToBytes(VM.activePlan.global().getPagesUsed());
  }


  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this includes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static Extent reservedMemory() {
    return Conversions.pagesToBytes(VM.activePlan.global().getPagesReserved());
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in bytes.
   *
   * @return The total amount of memory managed to the memory
   * management system, in bytes.
   */
  public static Extent totalMemory() {
    return HeapGrowthManager.getCurrentHeapSize();
  }

  /* Instance methods */

  /**
   * Return the total amount of memory managed to the memory
   * management system, in pages.
   *
   * @return The total amount of memory managed to the memory
   * management system, in pages.
   */
  public final int getTotalPages() {
    return totalMemory().toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
  }

  /**
   * Return the number of pages available for allocation.
   *
   * @return The number of pages available for allocation.
   */
  public int getPagesAvail() {
    return getTotalPages() - getPagesReserved();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  Sub-classes must override the getCopyReserve method,
   * as the arithmetic here is fixed.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public final int getPagesReserved() {
    return getPagesUsed() + getCollectionReserve();
  }

  /**
   * Return the number of pages reserved for collection.
   * In most cases this is a copy reserve, all subclasses that
   * manage a copying space must add the copying contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for collection.
   */
  public int getCollectionReserve() {
    return 0;
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getPagesUsed() {
    return loSpace.reservedPages() +
           immortalSpace.reservedPages() + metaDataSpace.reservedPages() +
           nonMovingSpace.reservedPages();
  }

  /**
   * Calculate the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   *
   * @return the number of pages a collection is required to free to satisfy
   * outstanding allocation requests.
   */
  public int getPagesRequired() {
    return loSpace.requiredPages() +
      metaDataSpace.requiredPages() + immortalSpace.requiredPages() +
      nonMovingSpace.requiredPages();
  }

  /**
   * The minimum number of pages a GC must have available after a collection
   * for us to consider the collection successful.
   */
  public int getHeapFullThreshold() {
    int threshold = (getTotalPages() * HEAP_FULL_PERCENTAGE) / 100;
    if (threshold < HEAP_FULL_MINIMUM) threshold = HEAP_FULL_MINIMUM;
    return threshold;
  }

  /**
   * Return the number of metadata pages reserved for use given the pending
   * allocation.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  public int getMetaDataPagesUsed() {
    return metaDataSpace.reservedPages();
  }

  /**
   * Return the cycle time at which this GC should complete.
   *
   * @return The time cap for this GC (i.e. the time by which it
   * should complete).
   */
  public static long getTimeCap() {
    return timeCap;
  }

  /****************************************************************************
   * Internal read/write barriers.
   */

  /**
   * Store an object reference
   *
   * @param slot The location of the reference
   * @param value The value to store
   */
  @Inline
  public void storeObjectReference(Address slot, ObjectReference value) {
    slot.store(value);
  }

  /**
   * Load an object reference
   *
   * @param slot The location of the reference
   * @return the object reference loaded from slot
   */
  @Inline
  public ObjectReference loadObjectReference(Address slot) {
    return slot.loadObjectReference();
  }

  /****************************************************************************
   * Collection.
   */

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @param space The space that triggered the poll.
   * @return true if a collection is required.
   */
  @LogicallyUninterruptible
  public final boolean poll(boolean spaceFull, Space space) {
    if (isCollectionTriggered()) {
      if (space == metaDataSpace) {
        /* This is not, in general, in a GC safe point. */
        return false;
      }
      /* Someone else initiated a collection, we should join it */
      logPoll(space, "Joining collection");
      VM.collection.joinCollection();
      return true;
    }

    if (collectionRequired(spaceFull)) {
      if (space == metaDataSpace) {
        /* In general we must not trigger a GC on metadata allocation since
         * this is not, in general, in a GC safe point.  Instead we initiate
         * an asynchronous GC, which will occur at the next safe point.
         */
        logPoll(space, "Asynchronous collection requested");
        setAwaitingAsyncCollection();
        return false;
      }
      logPoll(space, "Triggering collection");
      VM.collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }

    if (concurrentCollectionRequired()) {
      logPoll(space, "Triggering collection");
      VM.collection.triggerCollection(Collection.INTERNAL_PHASE_GC_TRIGGER);
      return true;
    }

    return false;
  }

  /**
   * Check whether an asynchronous collection is pending.<p>
   *
   * This is decoupled from the poll() mechanism because the
   * triggering of asynchronous collections can trigger write
   * barriers, which can trigger an asynchronous collection.  Thus, if
   * the triggering were tightly coupled with the request to alloc()
   * within the write buffer code, then inifinite regress could
   * result.  There is no race condition in the following code since
   * there is no harm in triggering the collection more than once,
   * thus it is unsynchronized.
   */
  @Inline
  public static void checkForAsyncCollection() {
    if (awaitingAsyncCollection && VM.collection.noThreadsInGC()) {
      awaitingAsyncCollection = false;
      VM.collection.triggerAsyncCollection(Collection.RESOURCE_GC_TRIGGER);
    }
  }

  /** Request an async GC */
  protected static void setAwaitingAsyncCollection() {
    awaitingAsyncCollection = true;
  }

  /**
   * Log a message from within 'poll'
   * @param space
   * @param message
   */
  private void logPoll(Space space, String message) {
    if (Options.verbose.getValue() >= 3) {
      Log.write("  [POLL] ");
      Log.write(space.getName());
      Log.write(": ");
      Log.writeln(message);
    }
  }

  /**
   * This method controls the triggering of a GC. It is called periodically
   * during allocation. Returns true to trigger a collection.
   *
   * @param spaceFull Space request failed, must recover pages within 'space'.
   * @return True if a collection is requested by the plan.
   */
  protected boolean collectionRequired(boolean spaceFull) {
    boolean stressForceGC = stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();

    return spaceFull || stressForceGC || heapFull;
  }

  /**
   * This method controls the triggering of an atomic phase of a concurrent
   * collection. It is called periodically during allocation.
   *
   * @return True if a collection is requested by the plan.
   */
  protected boolean concurrentCollectionRequired() {
    return false;
  }

  /**
   * Start GCspy server.
   *
   * @param port The port to listen on,
   * @param wait Should we wait for a client to connect?
   */
  @Interruptible
  public void startGCspyServer(int port, boolean wait) {
    VM.assertions.fail("startGCspyServer called on non GCspy plan");
  }

  /**
   * Can this object ever move.  Used by the VM to make decisions about
   * whether it needs to copy IO buffers etc.
   *
   * @param object The object in question
   * @return True if it is not possible that the object will ever move.
   */
  public boolean willNeverMove(ObjectReference object) {
    if (!VM.activePlan.constraints().movesObjects())
      return true;
    if (Space.isInSpace(LOS, object))
      return true;
    if (Space.isInSpace(IMMORTAL, object))
      return true;
    if (Space.isInSpace(VM_SPACE, object))
      return true;
    if (Space.isInSpace(NON_MOVING, object))
      return true;
    if (USE_CODE_SPACE && Space.isInSpace(SMALL_CODE, object))
      return true;
    if (USE_CODE_SPACE && Space.isInSpace(LARGE_CODE, object))
      return true;
    /*
     * Default to false- this preserves correctness over efficiency.
     * Individual plans should override for non-moving spaces they define.
     */
    return false;
  }

  /****************************************************************************
   * Specialized Methods
   */

  /**
   * Register specialized methods.
   */
   @Interruptible
  protected void registerSpecializedMethods() {
  }

  /**
   * Get the specialized scan with the given id.
   */
  public final Class<?> getSpecializedScanClass(int id) {
    return TransitiveClosure.getSpecializedScanClass(id);
  }
}
