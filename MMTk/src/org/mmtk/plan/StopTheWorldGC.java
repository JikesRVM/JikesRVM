/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.policy.Space;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.Log;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.utility.scan.Scan;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.Assert;
import org.mmtk.utility.Constants;
import org.mmtk.vm.Plan;
import org.mmtk.vm.PlanConstants;
import org.mmtk.vm.Scanning;
import org.mmtk.vm.Statistics;
import org.mmtk.vm.Collection;
import org.mmtk.vm.Memory;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This abstract class implments the core functionality for
 * stop-the-world collectors.  Stop-the-world collectors should
 * inherit from this class.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 * 
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class StopTheWorldGC extends BasePlan
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  // Global pools for load-balancing queues
  protected static SharedDeque valuePool = new SharedDeque(metaDataSpace, 1);
  protected static SharedDeque remsetPool = new SharedDeque(metaDataSpace, 1);
  protected static SharedDeque forwardPool = new SharedDeque(metaDataSpace, 1);
  protected static SharedDeque rootLocationPool = new SharedDeque(metaDataSpace, 1);
  protected static SharedDeque interiorRootPool = new SharedDeque(metaDataSpace, 2);

  // Statistics
  static Timer initTime = new Timer("init", false, true);
  static Timer rootTime = new Timer("root", false, true);
  static Timer scanTime = new Timer("scan", false, true);
  static Timer finalizeTime = new Timer("finalize", false, true);
  static Timer refTypeTime = new Timer("refType", false, true);
  static Timer finishTime = new Timer("finish", false, true);

  // GC state
  protected static boolean progress = true;  // are we making progress?
  protected static int required; // how many pages must this GC yeild? 

  // GC stress test
  private static long lastStressCumulativeCommittedPages = 0;  

  /****************************************************************************
   *
   * Instance variables
   */
  protected ObjectReferenceDeque values;  // gray objects
  protected AddressDeque remset;          // remset
  protected ObjectReferenceDeque forwardedObjects; // forwarded, unscanned obj
  protected AddressDeque rootLocations;   // root locs containing white objects
  protected AddressPairDeque interiorRootLocations; // interior root locations

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {}

  /**
   * Constructor
   */
  StopTheWorldGC() {
    values = new ObjectReferenceDeque("value", valuePool);
    valuePool.newClient();
    remset = new AddressDeque("remset", remsetPool);
    remsetPool.newClient();
    forwardedObjects = new ObjectReferenceDeque("forwarded", forwardPool);
    forwardPool.newClient();
    rootLocations = new AddressDeque("rootLoc", rootLocationPool);
    rootLocationPool.newClient();
    interiorRootLocations = new AddressPairDeque(interiorRootPool);
    interiorRootPool.newClient();
 }

  /****************************************************************************
   *
   * Collection
   *
   * Important notes:
   *   . Global actions are executed by only one thread
   *   . Thread-local actions are executed by all threads
   *   . The following order is guaranteed by BasePlan, with each
   *     separated by a synchronization barrier.
   *      1. globalPrepare()
   *      2. threadLocalPrepare()
   *      3. threadLocalRelease()
   *      4. globalRelease()
   */
  abstract protected void globalPrepare();
  abstract protected void threadLocalPrepare(int order);
  abstract protected void threadLocalRelease(int order);
  abstract protected void globalRelease();

  /**
   * Check whether a stress test GC is required
   */
  protected static final boolean stressTestGCRequired()
    throws InlinePragma {
    long pages = Space.cumulativeCommittedPages();
    if (initialized &&
        ((pages ^ lastStressCumulativeCommittedPages) > stressFactor.getPages())) {
      lastStressCumulativeCommittedPages = pages;
      return true;
    } else
      return false;
  }

  /**
   * Perform a collection.
   *
   * Important notes:
   *   . Global actions are executed by only one thread
   *   . Thread-local actions are executed by all threads
   *   . The following order is guaranteed by BasePlan, with each
   *     separated by a synchronization barrier.:
   *      1. globalPrepare()
   *      2. threadLocalPrepare()
   *      3. threadLocalRelease()
   *      4. globalRelease()
   */
  public void collect() {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(collectionsInitiated > 0);

    boolean designated = (Collection.rendezvous(4210) == 1);
    boolean timekeeper = Stats.gatheringStats() && designated;
    if (timekeeper) Stats.startGC();
    if (timekeeper) initTime.start();
    prepare();
    if (timekeeper) initTime.stop();

    if (timekeeper) rootTime.start();
    Scanning.computeAllRoots(rootLocations, interiorRootLocations);
    if (PlanConstants.WITH_GCSPY()) gcspyRoots(rootLocations, interiorRootLocations);
    if (timekeeper) rootTime.stop();

    // This should actually occur right before preCopyGC but
    // a spurious complaint about setObsolete would occur.
    // The upshot is that objects coped by preCopyGC are not
    // subject to the sanity checking.
    int order = Collection.rendezvous(4900);
    if (order == 1) {
      Scanning.resetThreadCounter();
      setGcStatus(GC_PROPER);    
    }
    Collection.rendezvous(4901);

    if (timekeeper) scanTime.start();
    processAllWork(); 
    if (timekeeper) scanTime.stop();

    if (!noReferenceTypes.getValue()) {
      if (timekeeper) refTypeTime.start();
      if (designated) ReferenceProcessor.processSoftReferences();
      if (designated) ReferenceProcessor.processWeakReferences();
      if (timekeeper) refTypeTime.stop();
    }
 
    if (noFinalizer.getValue()) {
      if (designated) Finalizer.kill();
    } else {
      if (timekeeper) finalizeTime.start();
      if (designated) Finalizer.moveToFinalizable(); 
      Collection.rendezvous(4220);
      if (timekeeper) finalizeTime.stop();
    }
      
    if (!noReferenceTypes.getValue()) {
      if (timekeeper) refTypeTime.start();
      if (designated) ReferenceProcessor.processPhantomReferences();
      if (timekeeper) refTypeTime.stop();
    }

    if (!noReferenceTypes.getValue() || !noFinalizer.getValue()) {
      if (timekeeper) scanTime.start();
      processAllWork();
      if (timekeeper) scanTime.stop();
    }

    if (timekeeper) finishTime.start();
    release();
    if (timekeeper) finishTime.stop();
    if (timekeeper) Stats.endGC();
    if (timekeeper) printPostStats();
  }

  /**
   * Prepare for a collection.
   */
  protected final void prepare() {
    long start = Statistics.cycles();
    int order = Collection.rendezvous(4230);
    if (order == 1) {
      setGcStatus(GC_PREPARE);
      baseGlobalPrepare(start);
    }
    Collection.rendezvous(4240);
    if (order == 1)
      for (int i=0; i<planCount; i++) {
        Plan p = plans[i];
        if (Collection.isNonParticipating(p)) 
          p.baseThreadLocalPrepare(NON_PARTICIPANT);
      }
    baseThreadLocalPrepare(order);
    Collection.rendezvous(4250);
    if (PlanConstants.MOVES_OBJECTS()) {
      Scanning.preCopyGCInstances();
      Collection.rendezvous(4260);
      if (order == 1) Scanning.resetThreadCounter();
      Collection.rendezvous(4270);
    }
  }

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>prepare()</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means performing generic operations and calling
   * <code>globalPrepare()</code>, which performs plan-specific
   * operations.
   *
   * @param start The time that this GC started
   */
  private final void baseGlobalPrepare(long start) {
    printPreStats();
    globalPrepare();
  }

  /**
   * Perform global prepare operations with respect to spaces that are
   * common to all subclasses.  This code is conditionally called by
   * subclasses (for example, a generational collector may call this
   * only when performing whole heap collection).
   */
  protected final void commonGlobalPrepare() {
    loSpace.prepare();
    immortalSpace.prepare(); 
    Memory.globalPrepareVMSpace();
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>prepare()</code> which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * After performing generic operations,
   * <code>threadLocalPrepare()</code> is called to perform
   * subclass-specific operations.
   *
   * @param order A unique ordering placed on the threads by the
   * caller's use of <code>rendezvous</code>.
   */
  public final void baseThreadLocalPrepare(int order) {
    if (order == NON_PARTICIPANT) {
      Collection.prepareNonParticipating((Plan) this);  
    }
    else {
      Collection.prepareParticipating((Plan) this);  
      Collection.rendezvous(4260);
    }
    if (verbose.getValue() >= 4) Log.writeln("  Preparing all collector threads for start");
    threadLocalPrepare(order);
  }

  /**
   * Perform localprepare operations with respect to spaces that are
   * common to all subclasses.  This code is conditionally called by
   * subclasses (for example, a generational collector may call this
   * only when performing whole heap collection).
   */
  protected final void commonLocalPrepare() {
    los.prepare();
    Memory.localPrepareVMSpace();
  }

  /**
   * Clean up after a collection
   */
  protected final void release() {
    if (verbose.getValue() >= 4) Log.writeln("  Preparing all collector threads for termination");
    int order = Collection.rendezvous(4270);
    if (PlanConstants.WITH_GCSPY()) gcspyPreRelease(); 
    baseThreadLocalRelease(order);
    if (order == 1) {
      int count = 0;
      for (int i=0; i<planCount; i++) {
        Plan p = plans[i];
        if (Collection.isNonParticipating(p)) {
          count++;
          ((StopTheWorldGC) p).baseThreadLocalRelease(NON_PARTICIPANT);
        }
      }
      if (verbose.getValue() >= 4) {
        Log.write("  There were "); Log.write(count);
        Log.writeln(" non-participating GC threads");
      }
    }
    order = Collection.rendezvous(4280);
    if (order == 1) {
      baseGlobalRelease();
      setGcStatus(NOT_IN_GC);    // GC is in progress until after release!
    }
    Collection.rendezvous(4290);
  }

  /**
   * Perform operations with <i>global</i> scope to clean up after a
   * collection.  This is called by <code>release()</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means performing generic operations and calling
   * <code>globalRelease()</code>, which performs plan-specific
   * operations.
   */
  private final void baseGlobalRelease() {
    globalRelease();
    valuePool.reset();
    remsetPool.reset();
    forwardPool.reset();
    rootLocationPool.reset();
    interiorRootPool.reset();
  }

  /**
   * Perform global release operations with respect to spaces that are
   * common to all subclasses.  This code is conditionally called by
   * subclasses (for example, a generational collector may call this
   * only when performing whole heap collection).
   */
  protected final void commonGlobalRelease() {
    loSpace.release();
    immortalSpace.release();
    Memory.globalReleaseVMSpace();
  }

  /**
   * Perform operations with <i>thread-local</i> scope to release
   * resources after a collection.  This is called by
   * <code>release()</code> which will ensure that <i>all threads</i>
   * execute this.
   */
  private final void baseThreadLocalRelease(int order) {
    values.reset();
    remset.reset();
    forwardedObjects.reset();
    rootLocations.reset();
    interiorRootLocations.reset();
    threadLocalRelease(order);
  }

  /**
   * Perform local release operations with respect to spaces that are
   * common to all subclasses.  This code is conditionally called by
   * subclasses (for example, a generational collector may call this
   * only when performing whole heap collection).
   */
  protected final void commonLocalRelease() { 
    los.release();
    Memory.localReleaseVMSpace();
  }

  /**
   * Return the number of pages reserved for use by the common spaces,
   * given the pending allocation.
   *
   * @return The number of pages reserved for use by the common
   * spaces, given the pending allocation.
   */
  protected static final int getCommonPagesReserved() {
    return loSpace.reservedPages() + immortalSpace.reservedPages() + metaDataSpace.reservedPages();
  }

  /**
   * Process all GC work.  This method iterates until all work queues
   * are empty.
   */
  private final void processAllWork() throws NoInlinePragma {

    if (verbose.getValue() >= 4) { Log.prependThreadId(); Log.writeln("  Working on GC in parallel"); }
    do {
      if (verbose.getValue() >= 5) { Log.prependThreadId(); Log.writeln("    processing forwarded (pre-copied) objects"); }
      while (!forwardedObjects.isEmpty()) {
        ObjectReference object = forwardedObjects.pop();
        scanForwardedObject(object);
      }
      if (verbose.getValue() >= 5) { Log.prependThreadId(); Log.writeln("    processing root locations"); }
      while (!rootLocations.isEmpty()) {
        Address loc = rootLocations.pop();
        traceObjectLocation(loc, true);
      }
      if (verbose.getValue() >= 5) { Log.prependThreadId(); Log.writeln("    processing interior root locations"); }
      while (!interiorRootLocations.isEmpty()) {
        ObjectReference obj = interiorRootLocations.pop1().toObjectReference();
        Address interiorLoc = interiorRootLocations.pop2();
        Address interior = interiorLoc.loadAddress();
        Address newInterior = traceInteriorReference(obj, interior, true);
        interiorLoc.store(newInterior);
      }
      if (verbose.getValue() >= 5) { Log.prependThreadId(); Log.writeln("    processing gray objects"); }
      while (!values.isEmpty()) {
        ObjectReference v = values.pop();
        Scan.scanObject(v);  // NOT traceObject
      }
      if (verbose.getValue() >= 5) { Log.prependThreadId(); Log.writeln("    processing remset"); }
      while (!remset.isEmpty()) {
        Address loc = remset.pop();
        traceObjectLocation(loc, false);
      }
      flushRememberedSets();
    } while (!(rootLocations.isEmpty() && interiorRootLocations.isEmpty()
               && values.isEmpty() && remset.isEmpty()));

    if (verbose.getValue() >= 4) { Log.prependThreadId(); Log.writeln("    waiting at barrier"); }
    Collection.rendezvous(4300);
  }

  /**
   * Flush any remembered sets pertaining to the current collection.
   * Non-generational collectors do nothing.
   */
  protected void flushRememberedSets() {}

  /**
   * Collectors that move objects <b>must</b> override this method.
   * It performs the deferred scanning of objects which are forwarded
   * during bootstrap of each copying collection.  Because of the
   * complexities of the collection bootstrap (such objects are
   * generally themselves gc-critical), the forwarding and scanning of
   * the objects must be dislocated.  It is an error for a non-moving
   * collector to call this method.
   *
   * @param object The forwarded object to be scanned
   */
  protected void scanForwardedObject(ObjectReference object) {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!PlanConstants.MOVES_OBJECTS());
  }

  /**
   * Print out plan-specific timing info
   */
  protected void printPlanTimes(boolean totals) {}

  /**
   * Print out statistics at the start of a GC
   */
  private void printPreStats() {
    if ((verbose.getValue() == 1) || (verbose.getValue() == 2)) {
      Log.write("[GC "); Log.write(Stats.gcCount());
      if (verbose.getValue() == 1) {
        Log.write(" Start "); 
        totalTime.printTotalSecs();
        Log.write(" s");
      } else {
        Log.write(" Start "); 
        totalTime.printTotalMillis();
        Log.write(" ms");
      }
      Log.write("   ");
      Log.write(Conversions.pagesToKBytes(Plan.getPagesUsed()));
      Log.write("KB ");
      Log.flush();
    }
    if (verbose.getValue() > 2) {
      Log.write("Collection "); Log.write(Stats.gcCount()); 
      Log.write(":        "); 
      printUsedPages();
      Log.write("  Before Collection: ");
      Space.printUsageMB();
      if (verbose.getValue() >= 4) {
        Log.write("                     ");
        Space.printUsagePages();
      }
    }
  }

  /**
   * Print out statistics at the end of a GC
   */
  protected final void printPostStats() {
    if ((verbose.getValue() == 1) || (verbose.getValue() == 2)) {
      Log.write("-> ");
      Log.writeDec(Conversions.pagesToBytes(Plan.getPagesUsed()).toWord().rshl(10));
      Log.write(" KB   ");
      if (verbose.getValue() == 1) {
        totalTime.printLast();
        Log.writeln(" ms]");
      } else {
        Log.write("End "); 
        totalTime.printTotal();
        Log.writeln(" ms]");
      }
    }
    if (verbose.getValue() > 2) {
      Log.write("   After Collection: ");
      Space.printUsageMB();
      if (verbose.getValue() >= 4) {
          Log.write("                     ");
          Space.printUsagePages();
      }
      Log.write("                     ");
      printUsedPages();
      Log.write("    Collection time: ");
      totalTime.printLast();
      Log.writeln(" seconds");
    }
  }
  
  private final void printUsedPages() {
      Log.write("reserved = "); 
      Log.write(Conversions.pagesToMBytes(Plan.getPagesReserved()));
      Log.write(" MB (");
      Log.write(Plan.getPagesReserved());
      Log.write(" pgs)");
      Log.write("      total = ");
      Log.write(Conversions.pagesToMBytes(getTotalPages()));
      Log.write(" MB (");
      Log.write(getTotalPages());
      Log.write(" pgs)");
      Log.writeln();
  }
}
