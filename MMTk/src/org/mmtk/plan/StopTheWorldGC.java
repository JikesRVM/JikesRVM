/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;
import com.ibm.JikesRVM.memoryManagers.vmInterface.SynchronizationBarrier;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
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
  static {
    valuePool = new SharedQueue(metaDataRPA, 1);
    locationPool = new SharedQueue(metaDataRPA, 1);
    rootLocationPool = new SharedQueue(metaDataRPA, 1);
    interiorRootPool = new SharedQueue(metaDataRPA, 2);
  }

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
  protected void collect() {
    VM_Interface.computeAllRoots(rootLocations, interiorRootLocations);
    processAllWork();
  }

  // These abstract methods are called in the order globalPrepare,
  // threadLocalPrepare, threadLocalRelease, and globalRelease.  They
  // are all separated by a barrier.
  abstract protected void globalPrepare();
  abstract protected void threadLocalPrepare(int order);
  abstract protected void threadLocalRelease(int order);
  abstract protected void globalRelease();

  /**
   * Prepare for a collection.
   */
  protected final void prepare() {
    double start = VM_Interface.now();
    int order = VM_CollectorThread.gcBarrier.rendezvous();
    if (order == 1)
      baseGlobalPrepare(start);
    baseThreadLocalPrepare(order);
    VM_CollectorThread.gcBarrier.rendezvous();
  }

  private final void baseGlobalPrepare(double start) {
    gcInProgress = true;
    gcCount++;
    gcStartTime = start;
    if (verbose == 1) {
      VM.sysWrite("[GC ", gcCount);
      VM.sysWrite(" start ", ((gcStartTime - bootTime)*1000));
      VM.sysWrite("ms ");
      VM.sysWrite(Conversions.pagesToBytes(Plan.getPagesUsed())>>10);
    }
    if (verbose > 2) {
      VM.sysWrite("Collection ", gcCount);
      VM.sysWrite(":      reserved = ", Plan.getPagesReserved());
      VM.sysWrite(" (s", Conversions.pagesToBytes(Plan.getPagesReserved()) / ( 1 << 20)); 
      VM.sysWrite(" Mb) ");
      VM.sysWrite("      trigger = ", getTotalPages());
      VM.sysWrite(" (", Conversions.pagesToBytes(getTotalPages()) / ( 1 << 20)); 
      VM.sysWriteln(" Mb) ");
      VM.sysWrite("  Before Collection: ");
      Plan.showUsage();
    }
    globalPrepare();
    VM_Interface.resetComputeAllRoots();
    VM_Interface.prepareNonParticipating(); // The will fix collector threads that are not participating in thie GC.
  }

  private final void baseThreadLocalPrepare(int order) {
    VM_Interface.prepareParticipating();      // Every participating thread needs to adjust its context registers.
    VM_CollectorThread.gcBarrier.rendezvous();
    if (verbose > 3) VM.sysWriteln("  Preparing all collector threads for start");
    threadLocalPrepare(order);
  }

  protected final void release() {
    int order = VM_CollectorThread.gcBarrier.rendezvous();
    if (verbose > 3) VM.sysWriteln("  Preparing all collector threads for termination");
    threadLocalRelease(order);
    order = VM_CollectorThread.gcBarrier.rendezvous();
    if (order == 1)
      baseGlobalRelease();
    threadLocalReset();
    VM_CollectorThread.gcBarrier.rendezvous();
  }

  private final void baseGlobalRelease() {
    globalRelease();
    if (verbose == 1) {
      VM.sysWrite("->");
      VM.sysWrite(Conversions.pagesToBytes(Plan.getPagesUsed())>>10);
      VM.sysWrite("KB ");
    }
    if (verbose > 2) {
      VM.sysWrite("   After Collection: ");
      Plan.showUsage();
      VM.sysWrite("   Collection ", gcCount);
      VM.sysWrite(":      reserved = ", Plan.getPagesReserved());
      VM.sysWrite(" (", Conversions.pagesToBytes(Plan.getPagesReserved())/(1<<20)); 
      VM.sysWrite(" Mb) ");
      VM.sysWrite("      trigger = ", getTotalPages());
      VM.sysWrite(" (", Conversions.pagesToBytes(getTotalPages())/(1<<20)); 
    }
    gcInProgress = false;    // GC is in progress until after release!
    gcStopTime = VM_Interface.now();
    if (verbose == 1) {
      VM.sysWrite("stop ", ((gcStopTime - bootTime)*1000));
      VM.sysWriteln("ms]");
    }
    if (verbose > 2) {
      VM.sysWrite("    Collection time: ", (gcStopTime - gcStartTime));
      VM.sysWriteln(" seconds");
    }
    locationPool.reset();
    // FIXME ** what about resetting other pools??
  }

  private final void threadLocalReset() {
    values.reset();
    locations.reset();
    rootLocations.reset();
    interiorRootLocations.reset();
  }

  private void processAllWork() throws VM_PragmaNoInline {

    if (verbose >= 4) VM.sysWriteln("  Working on GC in parallel");
    do {
      if (verbose >= 5) VM.sysWriteln("    processing root locations");
      while (!rootLocations.isEmpty()) {
	VM_Address loc = rootLocations.pop();
	if (verbose >= 6) VM.sysWriteln("      root location = ", loc);
	traceObjectLocation(loc, true);
      }
      if (verbose >= 5) VM.sysWriteln("    processing interior root locations");
      while (!interiorRootLocations.isEmpty()) {
	VM_Address obj = interiorRootLocations.pop1();
	VM_Address interiorLoc = interiorRootLocations.pop2();
	VM_Address interior = VM_Magic.getMemoryAddress(interiorLoc);
	VM_Address newInterior = traceInteriorReference(obj, interior, true);
	VM_Magic.setMemoryAddress(interiorLoc, newInterior);
      }
      if (verbose >= 5) VM.sysWriteln("    processing gray objects");
      while (!values.isEmpty()) {
	VM_Address v = values.pop();
	ScanObject.scan(v);  // NOT traceObject
      }
      if (verbose >= 5) VM.sysWriteln("    processing locations");
      while (!locations.isEmpty()) {
	VM_Address loc = locations.pop();
	traceObjectLocation(loc, false);
      }
      
    } while (!(rootLocations.isEmpty() && interiorRootLocations.isEmpty()
	       && values.isEmpty() && locations.isEmpty()));
  }
}
