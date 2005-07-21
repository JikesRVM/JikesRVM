/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.LargeObjectSpace;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.*;
import org.mmtk.utility.statistics.Timer;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Collection;
import org.mmtk.vm.Memory;

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
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods of PlanLocal allow fast, unsychronized access to functions such as
 * allocation and collection.
 *
 * The global instance defines and manages static resources
 * (such as memory and virtual memory resources).  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of MMTk plans.
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public abstract class Plan implements Uninterruptible, Constants {
  /****************************************************************************
   * Constants
   */
  /* GC State */
  public static final int NOT_IN_GC  = 0;   // this must be zero for C code
  public static final int GC_PREPARE = 1;   // before setup and obtaining root
  public static final int GC_PROPER  = 2;

  /* Polling */
  public static final int DEFAULT_POLL_FREQUENCY = (128<<10)>>LOG_BYTES_IN_PAGE;
  public static final int META_DATA_POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  /* Space Size Constants. */
  public static final int   IMMORTAL_MB = 32;
  public static final int   META_DATA_MB = 32;
  public static final int   META_DATA_PAGES = (META_DATA_MB<<20)>>LOG_BYTES_IN_PAGE;
  public static final int   META_DATA_FULL_THRESHOLD = META_DATA_PAGES >> 1;
  public static final float LOS_FRAC = (float) 0.1;

  /* Allocator Constants */
  public static final int ALLOC_DEFAULT        = 0;
  public static final int ALLOC_IMMORTAL       = 1;
  public static final int ALLOC_LOS            = 2;
  public static final int ALLOC_GCSPY          = 3;
  public static final int ALLOC_HOT_CODE       = ALLOC_DEFAULT;
  public static final int ALLOC_COLD_CODE      = ALLOC_DEFAULT;
  public static final int ALLOC_STACK          = ALLOC_DEFAULT;
  public static final int ALLOC_IMMORTAL_STACK = ALLOC_IMMORTAL;
  public static final int ALLOCATORS           = 4;

  /* Miscellaneous Constants */
  public static final int     LOS_SIZE_THRESHOLD = 8 * 1024;
  public static final int     NON_PARTICIPANT = 0;
  public static final boolean GATHER_WRITE_BARRIER_STATS = false;
  public static final int     DEFAULT_MIN_NURSERY = (256*1024) >> LOG_BYTES_IN_PAGE;
  public static final int     DEFAULT_MAX_NURSERY = MAX_INT;

  /****************************************************************************
   * Class variables
   */

  /** The space that holds any VM specific objects (e.g. a boot image) */
  public static final Space vmSpace = Memory.getVMSpace();

  /** Any immortal objects allocated after booting are allocated here. */
  public static final ImmortalSpace immortalSpace = new ImmortalSpace("immortal", DEFAULT_POLL_FREQUENCY, IMMORTAL_MB);
  
  /** All meta data that is used by MMTk is allocated (and accounted for) in the meta data space. */
  public static final RawPageSpace metaDataSpace = new RawPageSpace("meta", DEFAULT_POLL_FREQUENCY, META_DATA_MB);
  
  /** Large objects are allocated into a special large object space. */
  public static final LargeObjectSpace loSpace = new LargeObjectSpace("los", DEFAULT_POLL_FREQUENCY, LOS_FRAC);

  /* Space descriptors */
  public static final int IMMORTAL = immortalSpace.getDescriptor();
  public static final int VM       = vmSpace.getDescriptor();
  public static final int META     = metaDataSpace.getDescriptor();
  public static final int LOS      = loSpace.getDescriptor();

  /** Timer that counts total time */
  public static Timer totalTime = new Timer("time");

  /** Support for time-limited GCs */
  protected static long timeCap;

  static {}
  
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
    Options.ignoreSystemGC = new IgnoreSystemGC();
    Options.metaDataLimit = new MetaDataLimit();
    Options.nurserySize = new NurserySize();
    Options.variableSizeHeap = new VariableSizeHeap();
  }

  /****************************************************************************
   * Boot.
   */

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public void boot() throws InterruptiblePragma {
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
  public void postBoot() throws InterruptiblePragma {
    if (Options.verbose.getValue() > 2) Space.printVMMap();
    if (Options.verbose.getValue() > 0) Stats.startAll();
  }

  /**
   * The fullyBooted method is called by the runtime just before normal 
   * execution commences.
   */
  public void fullyBooted() throws InterruptiblePragma {
    initialized = true;
    exceptionReserve = (int) (getTotalPages() *
                              (1 - Collection.OUT_OF_MEMORY_THRESHOLD));
  }

  /**
   * The VM is about to exit.  Perform any clean up operations.
   *
   * @param value The exit value
   */
  public void notifyExit(int value) {
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
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   *
   * @param ref the object ref to the storage to be initialized
   * @param typeRef the type reference for the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param status the initial value of the status word
   * @return The new value of the status word
   */
  public Word setBootTimeGCBits(Offset ref, ObjectReference typeRef,
                                       int size, Word status)
    throws InlinePragma {
    return status; // nothing to do (no bytes of GC header)
  }



  /****************************************************************************
   * Collection.
   */

  /**
   * Perform a (global) collection phase.
   */
  public abstract void collectionPhase(int phase);

  /**
   * @return Whether last GC is a full GC.
   */
  public boolean isLastGCFull() {
    return true;
  }

  /**
   * @return Is current GC only collecting objects allocated since last GC.
   */
  public boolean isCurrentGCNursery() {
    return false;
  }

  private long lastStressPages = 0;

  /**
   * @return True is a stress test GC is required
   */
  public final boolean stressTestGCRequired()
    throws InlinePragma {
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

  protected static int     required = 0;
  protected static boolean progress = true;
  
  private static boolean   awaitingCollection = false;
  private static boolean   initialized = false;
  private static int       collectionsInitiated = 0;
  private static int       gcStatus = NOT_IN_GC; // shared variable
  private static int       exceptionReserve = 0;

  /** @return Is the memory management system initialized? */
  public static final boolean isInitialized() { 
    return initialized; 
  }
  
  /** @return The number of collections that have been initiated. */
  protected static final int getCollectionsInitiated() {
    return collectionsInitiated;
  }
  
  /** @return The amount of space reserved in case of an exception. */
  protected static final int getExceptionReserve() {
    return exceptionReserve;
  }
  
  /** Request an async GC */
  protected static final void setAwaitingCollection() {
    awaitingCollection = true;
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
  public static void checkForAsyncCollection() {
    if (awaitingCollection && Collection.noThreadsInGC()) {
      awaitingCollection = false;
      Collection.triggerAsyncCollection();
    }
  }

  /**
   * A collection has been initiated.  Increment the collectionInitiated
   * state variable appropriately.
   */
  public static void collectionInitiated() {
    collectionsInitiated++;
  }

  /**
   * A collection has fully completed.  Decrement the collectionInitiated
   * state variable appropriately.
   */
  public static void collectionComplete() {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(collectionsInitiated > 0);
    // FIXME The following will probably break async GC.  A better fix
    // is needed
    collectionsInitiated = 0;
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
  public static boolean gcInProgressProper () {
    return gcStatus == GC_PROPER;
  }

  /**
   * Sets the GC status.
   * 
   * @param s The new GC status.
   */
  public static void setGCStatus (int s) {
    Memory.isync();
    gcStatus = s;
    Memory.sync();
  }

  /**
   * A user-triggered GC has been initiated.  By default, do nothing,
   * but this may be overridden.
   */
  public void userTriggeredGC() {
  }

  /****************************************************************************
   * Space - Allocator mapping. See description for getOwnAllocator in PlanLocal
   * for a description of why this is important.
   */

  /**
   * Return the name of the space into which an allocator is
   * allocating.  The allocator, <code>a</code> may be assocaited with
   * any plan instance.
   *
   * @param a An allocator
   * @return The name of the space into which <code>a</code> is
   * allocating, or "<null>" if there is no space associated with
   * <code>a</code>.
   */
  public static final String getSpaceNameFromAllocatorAnyLocal(Allocator a) {
    Space space = getSpaceFromAllocatorAnyLocal(a);
    if (space == null)
      return "<null>";
    else
      return space.getName();
  }

  /**
   * Return the space into which an allocator is allocating.  The
   * allocator, <code>a</code> may be assocaited with any plan
   * instance.
   *
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  public static final Space getSpaceFromAllocatorAnyLocal(Allocator a) {
    for (int i = 0; i < ActivePlan.localCount(); i++) {
      Space space = ActivePlan.local(i).getSpaceFromAllocator(a);
      if (space != null)
        return space;
    }
    return null;
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
  public static void harnessBegin() throws InterruptiblePragma {
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
  public static void harnessEnd() {
    Stats.stopAll();
    Stats.printStats();
    insideHarness = false;
  }

  /****************************************************************************
   * Memory Accounting
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
  public static final Extent freeMemory() {
    return totalMemory().sub(usedMemory());
  }

  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this excludes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static final Extent usedMemory() {
    return Conversions.pagesToBytes(ActivePlan.global().getPagesUsed());
  }


  /**
   * Return the amount of <i>memory in use</i>, in bytes.  Note that
   * this includes unused memory that is held in reserve for copying,
   * and therefore unavailable for allocation.
   *
   * @return The amount of <i>memory in use</i>, in bytes.
   */
  public static final Extent reservedMemory() {
    return Conversions.pagesToBytes(ActivePlan.global().getPagesReserved());
  }

  /**
   * Return the total amount of memory managed to the memory
   * management system, in bytes.
   *
   * @return The total amount of memory managed to the memory
   * management system, in bytes.
   */
  public static final Extent totalMemory() {
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
    return getPagesUsed() + getCopyReserve();
  }

  /**
   * Return the number of pages reserved for copying.  Subclasses that
   * manage a copying space must add the copying contribution.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  public int getCopyReserve() {
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
           immortalSpace.reservedPages() +
           metaDataSpace.reservedPages();
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
  public static final long getTimeCap() {
    return timeCap;
  }

  /****************************************************************************
   * Collection.
   */

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.
   *
   * @param mustCollect Should a collection be forced.
   * @param space The space that triggered the poll.
   * @return true if a collection is required.
   */
  public abstract boolean poll(boolean mustCollect, Space space);

  /**
   * Start GC spy server.
   *
   * @param port The port to listen on,
   * @param wait Should we wait for a client to connect? 
   */
  public void startGCspyServer(int port, boolean wait) throws InterruptiblePragma {
    Assert.fail("startGCspyServer called on non GCspy plan");
  }

}
