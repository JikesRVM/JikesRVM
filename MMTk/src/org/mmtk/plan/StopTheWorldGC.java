/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Statistics;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;
import com.ibm.JikesRVM.memoryManagers.vmInterface.SynchronizationBarrier;


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

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  // Global pools for load-balancing queues
  protected static SharedQueue valuePool;
  protected static SharedQueue locationPool;
  protected static SharedQueue rootLocationPool;
  protected static SharedQueue interiorRootPool;

  // Timing variables
  protected static double gcStartTime;
  protected static double gcStopTime;

  // GC state
  protected static boolean progress = true;  // are we making progress?
  protected static int required; // how many pages must this GC yeild? 

  // GC stress test
  private static int lastStressPagesReserved = 0;  

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  protected AddressQueue values;          // gray objects
  protected AddressQueue locations;       // locations containing white objects
  protected AddressQueue rootLocations;   // root locs containing white objects
  protected AddressPairQueue interiorRootLocations; // interior root locations


  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    valuePool = new SharedQueue(metaDataRPA, 1);
    locationPool = new SharedQueue(metaDataRPA, 1);
    rootLocationPool = new SharedQueue(metaDataRPA, 1);
    interiorRootPool = new SharedQueue(metaDataRPA, 2);
  }

  /**
   * Constructor
   */
  StopTheWorldGC() {
    values = new AddressQueue("value", valuePool);
    valuePool.newClient();
    locations = new AddressQueue("loc", locationPool);
    locationPool.newClient();
    rootLocations = new AddressQueue("rootLoc", rootLocationPool);
    rootLocationPool.newClient();
    interiorRootLocations = new AddressPairQueue(interiorRootPool);
    interiorRootPool.newClient();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //
  // Important notes:
  //   . Global actions are executed by only one thread
  //   . Thread-local actions are executed by all threads
  //   . The following order is guaranteed by BasePlan, with each
  //     separated by a synchronization barrier.
  //      1. globalPrepare()
  //      2. threadLocalPrepare()
  //      3. threadLocalRelease()
  //      4. globalRelease()
  //
  abstract protected void globalPrepare();
  abstract protected void threadLocalPrepare(int order);
  abstract protected void threadLocalRelease(int order);
  abstract protected void globalRelease();

  /**
   * Check whether a stress test GC is required
   */
  protected static final boolean stressTestGCRequired()
    throws VM_PragmaInline {
    int pagesReserved = Plan.getPagesReserved();
    if (initialized &&
	((pagesReserved ^ lastStressPagesReserved) > Options.stressTest)) {
      lastStressPagesReserved = pagesReserved;
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
  public void collect () {

    boolean designated = (VM_CollectorThread.gcBarrier.rendezvous() == 1);

    if (designated) Statistics.initTime.start();
    prepare();        
    if (designated) Statistics.initTime.stop();

    if (designated) Statistics.rootTime.start();
    VM_Interface.computeAllRoots(rootLocations, interiorRootLocations);
    if (designated) Statistics.rootTime.stop();

    if (designated) Statistics.scanTime.start();
    processAllWork(); 
    if (designated) Statistics.scanTime.stop();

    ReferenceProcessor.moveSoftReferencesToReadyList();
    ReferenceProcessor.moveWeakReferencesToReadyList();
 
    if (Options.noFinalizer) {

	    if (designated) Finalizer.kill();
    }
    
    else {
    if (designated) Statistics.finalizeTime.start();
    if (designated) Finalizer.moveToFinalizable(); 
    VM_CollectorThread.gcBarrier.rendezvous();
    if (designated) Statistics.finalizeTime.stop();

    ReferenceProcessor.movePhantomReferencesToReadyList();

    if (designated) Statistics.scanTime.start();
    processAllWork();
    if (designated) Statistics.scanTime.stop();
    }

    if (designated) Statistics.finishTime.start();
    release();
    if (designated) Statistics.finishTime.stop();
  }

  /**
   * Prepare for a collection.
   */
  protected final void prepare() {
    double start = VM_Interface.now();
    int order = VM_CollectorThread.gcBarrier.rendezvous();
    if (order == 1)
      baseGlobalPrepare(start);
    VM_CollectorThread.gcBarrier.rendezvous();
    if (order == 1)
      for (int i=0; i<planCount; i++) {
	Plan p = plans[i];
	if (VM_Interface.isNonParticipating(p))
	  p.baseThreadLocalPrepare(NON_PARTICIPANT);
      }
    baseThreadLocalPrepare(order);
    VM_CollectorThread.gcBarrier.rendezvous();
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
  private final void baseGlobalPrepare(double start) {
    gcInProgress = true;
    Statistics.gcCount++;
    gcStartTime = start;
    if ((verbose == 1) || (verbose == 2)) {
      VM_Interface.sysWrite("[GC ",Statistics.gcCount);
      if (verbose == 1) 
	VM_Interface.sysWrite(" Start ",(gcStartTime - bootTime)," s");
      else	
	VM_Interface.sysWrite(" Start ",1000*(gcStartTime - bootTime)," ms");
      VM_Interface.sysWrite("   ",Conversions.pagesToBytes(Plan.getPagesUsed())>>10," KB ");
    }
    if (verbose > 2) {
      VM_Interface.sysWrite("Collection ",Statistics.gcCount);
      VM_Interface.sysWrite(":        reserved = "); writePages(Plan.getPagesReserved(), MB_PAGES);
      VM_Interface.sysWrite("      total = "); writePages(getTotalPages(), MB_PAGES);
      VM_Interface.sysWriteln();
      VM_Interface.sysWrite("  Before Collection: ");
      MemoryResource.showUsage(MB);
      if (verbose >= 4) {
	  VM_Interface.sysWrite("                     ");
	  MemoryResource.showUsage(PAGES);
      }
    }
    globalPrepare();
    VM_Interface.resetComputeAllRoots();
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
    if (order == NON_PARTICIPANT)
      VM_Interface.prepareNonParticipating((Plan) this);  
    else
      VM_Interface.prepareParticipating((Plan) this);  
    VM_CollectorThread.gcBarrier.rendezvous();
    if (verbose >= 4) VM_Interface.sysWriteln("  Preparing all collector threads for start");
    threadLocalPrepare(order);
  }

  /**
   * Clean up after a collection
   */
  protected final void release() {
    if (verbose >= 4) VM_Interface.sysWriteln("  Preparing all collector threads for termination");
    int order = VM_CollectorThread.gcBarrier.rendezvous();
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
      if (verbose >= 4) VM_Interface.sysWriteln("  There were ",count," non-participating GC threads");
    }
    order = VM_CollectorThread.gcBarrier.rendezvous();
    if (order == 1)
      baseGlobalRelease();
    VM_CollectorThread.gcBarrier.rendezvous();
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
    gcInProgress = false;    // GC is in progress until after release!
    gcStopTime = VM_Interface.now();
    if ((verbose == 1) || (verbose == 2)) {
      VM_Interface.sysWrite("-> ");
      VM_Interface.sysWrite(Conversions.pagesToBytes(Plan.getPagesUsed())>>10," KB   ");
      if (verbose == 1)
	VM_Interface.sysWriteln(1000 * (gcStopTime - gcStartTime)," ms]");
      else
	VM_Interface.sysWriteln("End ",1000 * (gcStopTime - bootTime)," ms]");
    }
    if (verbose > 2) {
      VM_Interface.sysWrite("   After Collection: ");
      MemoryResource.showUsage(MB);
      if (verbose >= 4) {
	  VM_Interface.sysWrite("                     ");
	  MemoryResource.showUsage(PAGES);
      }
      VM_Interface.sysWrite("                     reserved = "); writePages(Plan.getPagesReserved(), MB_PAGES);
      VM_Interface.sysWrite("      total = "); writePages(getTotalPages(), MB_PAGES);
      VM_Interface.sysWriteln();
      VM_Interface.sysWrite("    Collection time: ");
      VM_Interface.sysWrite(gcStopTime - gcStartTime,3);
      VM_Interface.sysWriteln(" seconds");
    }
    valuePool.reset();
    locationPool.reset();
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
    locations.reset();
    rootLocations.reset();
    interiorRootLocations.reset();
    threadLocalRelease(order);
  }

  /**
   * Process all GC work.  This method iterates until all work queues
   * are empty.
   */
  private final void processAllWork() throws VM_PragmaNoInline {

    if (verbose >= 4) VM_Interface.psysWriteln("  Working on GC in parallel");
    flushRememberedSets();
    do {
      if (verbose >= 5) VM_Interface.psysWriteln("    processing root locations");
      while (!rootLocations.isEmpty()) {
	VM_Address loc = rootLocations.pop();
	traceObjectLocation(loc, true);
      }
      if (verbose >= 5) VM_Interface.psysWriteln("    processing interior root locations");
      while (!interiorRootLocations.isEmpty()) {
	VM_Address obj = interiorRootLocations.pop1();
	VM_Address interiorLoc = interiorRootLocations.pop2();
	VM_Address interior = VM_Magic.getMemoryAddress(interiorLoc);
	VM_Address newInterior = traceInteriorReference(obj, interior, true);
	VM_Magic.setMemoryAddress(interiorLoc, newInterior);
      }
      if (verbose >= 5) VM_Interface.psysWriteln("    processing gray objects");
      while (!values.isEmpty()) {
	VM_Address v = values.pop();
	ScanObject.scan(v);  // NOT traceObject
      }
      if (verbose >= 5) VM_Interface.psysWriteln("    processing locations");
      while (!locations.isEmpty()) {
	VM_Address loc = locations.pop();
	traceObjectLocation(loc, false);
      }
      flushRememberedSets();
    } while (!(rootLocations.isEmpty() && interiorRootLocations.isEmpty()
	       && values.isEmpty() && locations.isEmpty()));

    if (verbose >= 4) VM_Interface.psysWriteln("    waiting at barrier");
    VM_CollectorThread.gcBarrier.rendezvous();
  }

  /**
   * Flush any remembered sets pertaining to the current collection.
   * Non-generational collectors do nothing.
   */
  protected void flushRememberedSets() {}

}
