/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan;

import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.Log;
import org.mmtk.utility.Options;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.utility.scan.Scan;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.VM_Interface;
import org.mmtk.vm.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;


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
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  // Global pools for load-balancing queues
  protected static SharedDeque valuePool = new SharedDeque(metaDataRPA, 1);
  protected static SharedDeque remsetPool = new SharedDeque(metaDataRPA, 1);
  protected static SharedDeque forwardPool = new SharedDeque(metaDataRPA, 1);
  protected static SharedDeque rootLocationPool = new SharedDeque(metaDataRPA, 1);
  protected static SharedDeque interiorRootPool = new SharedDeque(metaDataRPA, 2);

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
  protected AddressDeque values;          // gray objects
  protected AddressDeque remset;          // remset (containing white objects)
  protected AddressDeque forwardedObjects;// forwarded, unscanned objects
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
    values = new AddressDeque("value", valuePool);
    valuePool.newClient();
    remset = new AddressDeque("loc", remsetPool);
    remsetPool.newClient();
    forwardedObjects = new AddressDeque("forwarded", forwardPool);
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
    throws VM_PragmaInline {
    long pages = MemoryResource.getCumulativeCommittedPages();
    if (initialized &&
        ((pages ^ lastStressCumulativeCommittedPages) > Options.stressPages)) {
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
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(collectionsInitiated > 0);

    boolean designated = (VM_Interface.rendezvous(4210) == 1);
    boolean timekeeper = Stats.gatheringStats() && designated;
    if (timekeeper) Stats.startGC();
    if (timekeeper) initTime.start();
    prepare();
    if (VM_Interface.GCSPY)
      gcspyPrepare();
    if (timekeeper) initTime.stop();

    if (timekeeper) rootTime.start();
    VM_Interface.computeAllRoots(rootLocations, interiorRootLocations);
    if (VM_Interface.GCSPY)
      gcspyRoots(rootLocations, interiorRootLocations);
    if (timekeeper) rootTime.stop();

    // This should actually occur right before preCopyGC but
    // a spurious complaint about setObsolete would occur.
    // The upshot is that objects coped by preCopyGC are not
    // subject to the sanity checking.
    int order = VM_Interface.rendezvous(4900);
    if (order == 1) {
      VM_Interface.resetThreadCounter();
      setGcStatus(GC_PROPER);    
    }
    VM_Interface.rendezvous(4901);

    if (timekeeper) scanTime.start();
    processAllWork(); 
    if (timekeeper) scanTime.stop();

    if (!Options.noReferenceTypes) {
      if (timekeeper) refTypeTime.start();
      if (designated) ReferenceProcessor.processSoftReferences();
      if (designated) ReferenceProcessor.processWeakReferences();
      if (timekeeper) refTypeTime.stop();
    }
 
    if (Options.noFinalizer) {
      if (designated) Finalizer.kill();
    } else {
      if (timekeeper) finalizeTime.start();
      if (designated) Finalizer.moveToFinalizable(); 
      VM_Interface.rendezvous(4220);
      if (timekeeper) finalizeTime.stop();
    }
      
    if (!Options.noReferenceTypes) {
      if (timekeeper) refTypeTime.start();
      if (designated) ReferenceProcessor.processPhantomReferences();
      if (timekeeper) refTypeTime.stop();
    }

    if (!Options.noReferenceTypes || !Options.noFinalizer) {
      if (timekeeper) scanTime.start();
      processAllWork();
      if (timekeeper) scanTime.stop();
    }

    if (timekeeper) finishTime.start();
    if (VM_Interface.GCSPY)
      gcspyPreRelease();
    release();
    if (VM_Interface.GCSPY)
      gcspyPostRelease();
    if (timekeeper) finishTime.stop();
    if (timekeeper) Stats.endGC();
    if (timekeeper) printStats();
  }

  /**
   * Prepare for a collection.
   */
  protected final void prepare() {
    long start = VM_Interface.cycles();
    int order = VM_Interface.rendezvous(4230);
    if (order == 1) {
      setGcStatus(GC_PREPARE);
      baseGlobalPrepare(start);
    }
    VM_Interface.rendezvous(4240);
    if (order == 1)
      for (int i=0; i<planCount; i++) {
        Plan p = plans[i];
        if (VM_Interface.isNonParticipating(p)) 
          p.baseThreadLocalPrepare(NON_PARTICIPANT);
      }
    baseThreadLocalPrepare(order);
    VM_Interface.rendezvous(4250);
    if (Plan.MOVES_OBJECTS) {
      VM_Interface.preCopyGCInstances();
      VM_Interface.rendezvous(4260);
      if (order == 1) VM_Interface.resetThreadCounter();
      VM_Interface.rendezvous(4270);
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
    if ((Options.verbose == 1) || (Options.verbose == 2)) {
      Log.write("[GC "); Log.write(Stats.gcCount());
      if (Options.verbose == 1) {
        Log.write(" Start "); 
        totalTime.printTotalSecs();
        Log.write(" s");
      } else {
        Log.write(" Start "); 
        totalTime.printTotalMillis();
        Log.write(" ms");
      }
      Log.write("   ");
      Log.write(Conversions.pagesToBytes(Plan.getPagesUsed()).toWord().rshl(10).toInt());
      Log.write(" KB ");
      Log.flush();
    }
    if (Options.verbose > 2) {
      Log.write("Collection "); Log.write(Stats.gcCount());
      Log.write(":        reserved = "); writePages(Plan.getPagesReserved(), MB_PAGES);
      Log.write("      total = "); writePages(getTotalPages(), MB_PAGES);
      Log.writeln();
      Log.write("  Before Collection: ");
      MemoryResource.showUsage(MB);
      if (Options.verbose >= 4) {
        Log.write("                     ");
        MemoryResource.showUsage(PAGES);
      }
    }
    globalPrepare();
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
      VM_Interface.prepareNonParticipating((Plan) this);  
    }
    else {
      VM_Interface.prepareParticipating((Plan) this);  
      VM_Interface.rendezvous(4260);
    }
    if (Options.verbose >= 4) Log.writeln("  Preparing all collector threads for start");
    threadLocalPrepare(order);
  }

  /**
   * Clean up after a collection
   */
  protected final void release() {
    if (Options.verbose >= 4) Log.writeln("  Preparing all collector threads for termination");
    int order = VM_Interface.rendezvous(4270);
    baseThreadLocalRelease(order);
    if (order == 1) {
      int count = 0;
      for (int i=0; i<planCount; i++) {
        Plan p = plans[i];
        if (VM_Interface.isNonParticipating(p)) {
          count++;
          ((StopTheWorldGC) p).baseThreadLocalRelease(NON_PARTICIPANT);
        }
      }
      if (Options.verbose >= 4) {
        Log.write("  There were "); Log.write(count);
        Log.writeln(" non-participating GC threads");
      }
    }
    order = VM_Interface.rendezvous(4280);
    if (order == 1) {
      baseGlobalRelease();
      setGcStatus(NOT_IN_GC);    // GC is in progress until after release!
    }
    VM_Interface.rendezvous(4290);
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
   * Process all GC work.  This method iterates until all work queues
   * are empty.
   */
  private final void processAllWork() throws VM_PragmaNoInline {

    if (Options.verbose >= 4) { Log.prependThreadId(); Log.writeln("  Working on GC in parallel"); }
    do {
      if (Options.verbose >= 5) { Log.prependThreadId(); Log.writeln("    processing forwarded (pre-copied) objects"); }
      while (!forwardedObjects.isEmpty()) {
        VM_Address object = forwardedObjects.pop();
        scanForwardedObject(object);
      }
      if (Options.verbose >= 5) { Log.prependThreadId(); Log.writeln("    processing root locations"); }
      while (!rootLocations.isEmpty()) {
        VM_Address loc = rootLocations.pop();
        traceObjectLocation(loc, true);
      }
      if (Options.verbose >= 5) { Log.prependThreadId(); Log.writeln("    processing interior root locations"); }
      while (!interiorRootLocations.isEmpty()) {
        VM_Address obj = interiorRootLocations.pop1();
        VM_Address interiorLoc = interiorRootLocations.pop2();
        VM_Address interior = VM_Magic.getMemoryAddress(interiorLoc);
        VM_Address newInterior = traceInteriorReference(obj, interior, true);
        VM_Magic.setMemoryAddress(interiorLoc, newInterior);
      }
      if (Options.verbose >= 5) { Log.prependThreadId(); Log.writeln("    processing gray objects"); }
      while (!values.isEmpty()) {
        VM_Address v = values.pop();
	Scan.scanObject(v);  // NOT traceObject
      }
      if (Options.verbose >= 5) { Log.prependThreadId(); Log.writeln("    processing remset"); }
      while (!remset.isEmpty()) {
        VM_Address loc = remset.pop();
        traceObjectLocation(loc, false);
      }
      flushRememberedSets();
    } while (!(rootLocations.isEmpty() && interiorRootLocations.isEmpty()
               && values.isEmpty() && remset.isEmpty()));

    if (Options.verbose >= 4) { Log.prependThreadId(); Log.writeln("    waiting at barrier"); }
    VM_Interface.rendezvous(4300);
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
  protected void scanForwardedObject(VM_Address object) {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(!Plan.MOVES_OBJECTS);
  }

  /**
   * Print out plan-specific timing info
   */
  protected void printPlanTimes(boolean totals) {}

  /**
   * Print out statistics for last GC
   */
  private final void printStats() {
    if ((Options.verbose == 1) || (Options.verbose == 2)) {
      Log.write("-> ");
      Log.write(Conversions.pagesToBytes(Plan.getPagesUsed()).toWord().rshl(10).toInt());
      Log.write(" KB   ");
      if (Options.verbose == 1) {
        totalTime.printLast();
        Log.writeln(" ms]");
      } else {
        Log.write("End "); 
        totalTime.printTotal();
        Log.writeln(" ms]");
      }
    }
    if (Options.verbose > 2) {
      Log.write("   After Collection: ");
      MemoryResource.showUsage(MB);
      if (Options.verbose >= 4) {
          Log.write("                     ");
          MemoryResource.showUsage(PAGES);
      }
      Log.write("                     reserved = "); writePages(Plan.getPagesReserved(), MB_PAGES);
      Log.write("      total = "); writePages(getTotalPages(), MB_PAGES);
      Log.writeln();
      Log.write("    Collection time: ");
      totalTime.printLast();
      Log.writeln(" seconds");
    }
  }
}
