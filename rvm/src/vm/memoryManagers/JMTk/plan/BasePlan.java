/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * All rights reserved.
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AddressSet;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AddressPairSet;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AddressTripleSet;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanThread;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanStatics;
import com.ibm.JikesRVM.memoryManagers.vmInterface.SynchronizationBarrier;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Offset;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_ObjectModel;

/**
 *
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class BasePlan 
  // extends BumpPointer 
implements Constants, VM_Uninterruptible {

  public final static String Id = "$Id$"; 

  public static int verbose = 0;

  protected AddressQueue values;          // gray objects
  protected AddressQueue locations;       // locations containing white objects
  protected AddressQueue rootLocations;   // root locations containing white objects
  protected AddressPairQueue interiorRootLocations; // interior root locations
  protected static SharedQueue valuePool;
  protected static SharedQueue locationPool;
  protected static SharedQueue rootLocationPool;
  protected static SharedQueue interiorRootPool;
  private static MonotoneVMResource metaDataVM;
  protected static MemoryResource metaDataMR;
  protected static RawPageAllocator metaDataRPA;

  protected static final boolean GATHER_WRITE_BARRIER_STATS = false;

  protected static final EXTENT       SEGMENT_SIZE = 0x10000000;
  protected static final int          SEGMENT_MASK = SEGMENT_SIZE - 1;
  protected static final VM_Address     BOOT_START = VM_Address.fromInt(VM_Interface.bootImageAddress);
  protected static final EXTENT          BOOT_SIZE = SEGMENT_SIZE - (VM_Interface.bootImageAddress & SEGMENT_MASK);   // use the remainder of the segment
  protected static final VM_Address       BOOT_END = BOOT_START.add(BOOT_SIZE);
  protected static final VM_Address IMMORTAL_START = BOOT_START;
  protected static final EXTENT      IMMORTAL_SIZE = BOOT_SIZE + 16 * 1024 * 1024;
  protected static final VM_Address   IMMORTAL_END = IMMORTAL_START.add(IMMORTAL_SIZE);
  protected static final VM_Address META_DATA_START = IMMORTAL_END;
  protected static final EXTENT     META_DATA_SIZE  = 64 * 1024 * 1024;
  protected static final VM_Address   META_DATA_END   = META_DATA_START.add(META_DATA_SIZE);  
  protected static final VM_Address   PLAN_START  = META_DATA_END;

  private static final int META_DATA_POLL_FREQUENCY = (1<<31) - 1; // never
  protected static final int DEFAULT_POLL_FREQUENCY = (128*1024)>>LOG_PAGE_SIZE;
  protected static final int DEFAULT_LOS_SIZE_THRESHOLD = 16 * 1024;
  protected static final int NON_PARTICIPANT = 0;

  static {
    metaDataMR = new MemoryResource(META_DATA_POLL_FREQUENCY);
    metaDataVM = new MonotoneVMResource("Meta data", metaDataMR, META_DATA_START, META_DATA_SIZE, VMResource.META_DATA);
    metaDataRPA = new RawPageAllocator(metaDataVM, metaDataMR);
    valuePool = new SharedQueue(metaDataRPA, 1);
    locationPool = new SharedQueue(metaDataRPA, 1);
    rootLocationPool = new SharedQueue(metaDataRPA, 1);
    interiorRootPool = new SharedQueue(metaDataRPA, 2);
  }


  BasePlan() {
    id = count++;
    values = new AddressQueue("value", valuePool);
    valuePool.newClient();
    locations = new AddressQueue("loc", locationPool);
    locationPool.newClient();
    rootLocations = new AddressQueue("rootLoc", rootLocationPool);
    rootLocationPool.newClient();
    interiorRootLocations = new AddressPairQueue(interiorRootPool);
    interiorRootPool.newClient();
  }

  /**
   * The boot method is called early in the boot process before any allocation.
   */
  static public void boot() throws VM_PragmaInterruptible {
    bootTime = VM_Time.now();
  }

  /**
   * The boot method is called by the runtime immediately after command-line
   * arguments are available.  Note that allocation must be supported
   * prior to this point because the runtime infrastructure may
   * require allocation in order to parse the command line arguments.
   * For this reason all plans should operate gracefully on the
   * default minimum heap size until the point that boot is called.
   */
  static public void postBoot() {
  }

  protected static boolean gcInProgress = false;    // This flag should be turned on/off by subclasses.
  protected static int gcCount = 0;
  private static int count = 0;                     // Number of plan instances in existence
  private int id = 0;                               // Zero-based id of plan instance;

  static public boolean gcInProgress() {
    return gcInProgress;
  }

  static public int gcCount() {
    return gcCount;
  }

  static public RawPageAllocator getMetaDataRPA() {
    return metaDataRPA;
  }

  /**
   * Prepare for a collection.  In this case, it means flipping
   * semi-spaces and preparing each of the collectors.
   */
  protected final void prepare() {
    SynchronizationBarrier barrier = VM_CollectorThread.gcBarrier;
    double tmp = VM_Time.now();
    int order = barrier.rendezvous();
    if (order == 1) {
      gcInProgress = true;
      gcCount++;
      startTime = tmp;
      if (verbose == 1) {
	VM.sysWrite("[GC ", gcCount);
	VM.sysWrite(" start ", ((startTime - bootTime)*1000));
	VM.sysWrite("ms ");
      }
      singlePrepare();
      resetComputeRoots();
      VM_Interface.prepareNonParticipating(); // The will fix collector threads that are not participating in thie GC.
    }
    VM_Interface.prepareParticipating();      // Every participating thread needs to adjust its context registers.
    order = barrier.rendezvous();
    if (verbose > 3) VM.sysWriteln("  Preparing all collector threads for start");
    allPrepare(order);
    barrier.rendezvous();
  }

  protected final void release() {
    SynchronizationBarrier barrier = VM_CollectorThread.gcBarrier;
    int order = barrier.rendezvous();
    if (verbose > 3) VM.sysWriteln("  Preparing all collector threads for termination");
    allRelease(order);
    order = barrier.rendezvous();
    if (order == 1) {
      singleRelease();
      gcInProgress = false;    // GC is in progress until after release!
      stopTime = VM_Time.now();
      if (verbose == 1) {
	VM.sysWrite("stop ", ((stopTime - bootTime)*1000));
	VM.sysWriteln("ms]");
      }
      if (verbose > 2) {
	VM.sysWrite("    Collection time: ", (stopTime - startTime));
	VM.sysWriteln(" seconds");
      }
      locationPool.reset();
    }
    values.reset();
    locations.reset();
    rootLocations.reset();
    interiorRootLocations.reset();
    barrier.rendezvous();
  }

  // These abstract methods are called in the order singlePrepare, allPrepare, allRelease, 
  // and singleRelease.  They are all separated by a barrier.
  abstract protected void singlePrepare();
  abstract protected void allPrepare(int order);
  abstract protected void allRelease(int order);
  abstract protected void singleRelease();

  static SynchronizedCounter threadCounter = new SynchronizedCounter();
  static double bootTime;
  static double startTime;
  static double stopTime;

  private void resetComputeRoots() {
    threadCounter.reset();
  }

  private void computeRoots() {

    AddressPairQueue codeLocations = VM_Interface.MOVES_OBJECTS ? interiorRootLocations : null;

    //    fetchRemsets(locations);
    ScanStatics.scanStatics(rootLocations);

    while (true) {
      int threadIndex = threadCounter.increment();
      if (threadIndex >= VM_Scheduler.threads.length) break;
      VM_Thread th = VM_Scheduler.threads[threadIndex];
      if (th == null) continue;
      // VM.sysWrite("Proc ", VM_Processor.getCurrentProcessor().id); VM.sysWriteln(" scanning thread ", threadIndex);
      // See comment of ScanThread.scanThread
      //
      VM_Address thAddr = VM_Magic.objectAsAddress(th);
      VM_Thread th2 = VM_Magic.addressAsThread(Plan.traceObject(thAddr, true));
      if (VM_Magic.objectAsAddress(th2).EQ(thAddr))
	ScanObject.rootScan(thAddr);
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.stack));
      if (th.jniEnv != null) {
	ScanObject.rootScan(VM_Magic.objectAsAddress(th.jniEnv));
	ScanObject.rootScan(VM_Magic.objectAsAddress(th.jniEnv.JNIRefs));
      }
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.contextRegisters));
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.contextRegisters.gprs));
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.hardwareExceptionRegisters));
      ScanObject.rootScan(VM_Magic.objectAsAddress(th.hardwareExceptionRegisters.gprs));
      ScanThread.scanThread(th2, rootLocations, codeLocations);
    }
    ScanObject.rootScan(VM_Magic.objectAsAddress(VM_Scheduler.threads));
  }

  // Add a gray object
  //
  static public void enqueue(VM_Address obj) throws VM_PragmaInline {
    VM_Interface.getPlan().values.push(obj);
  }

  private void processAllWork() throws VM_PragmaNoInline {

    if (verbose >= 4) VM.sysWriteln("  Working on GC in parallel");
    while (true) {
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

      if (rootLocations.isEmpty() && interiorRootLocations.isEmpty() && values.isEmpty() && locations.isEmpty())
	break;
    }

  }

  protected void collect() {
    computeRoots();
    processAllWork();
  }

  public static int getTotalPages() throws VM_PragmaUninterruptible { 
    heapPages = Conversions.bytesToPages(Options.initialHeapSize);
    return heapPages; 
  }

  private static int heapPages;


  public static void showPlans() {
    for (int i=0; i<VM_Scheduler.processors.length; i++) {
      VM_Processor p = VM_Scheduler.processors[i];
      if (p == null) continue;
      VM.sysWrite(i, ": ");
      VM_Interface.getPlanFromProcessor(p).show();
    }
  }

  /*
   * This method should be called whenever an error is encountered.
   *
   */
  public void error(String str) {
    Plan.showUsage();
    VM.sysFail(str);
  }

  /**
   * Perform a write barrier operation for the putField bytecode.<p> <b>By default do nothing,
   * override if appropriate.</b>
   *
   * @param srcObj The address of the object containing the pointer to
   * be written to
   * @param offset The offset from srcObj of the slot the pointer is to be written into
   * @param tgt The address to which the source will point
   */
  public void putFieldWriteBarrier(VM_Address srcObj, int offset, VM_Address tgt){
  }

  /**
   * Perform a write barrier operation for the putStatic bytecode.<p> <b>By default do nothing,
   * override if appropriate.</b>
   *
   * @param srcObj The address of the object containing the pointer to
   * be written to
   * @param offset The offset from static table (JTOC) of the slot the pointer is to be written into
   * @param tgt The address to which the source will point
   */
  public void putStaticWriteBarrier(VM_Address slot, VM_Address tgt){
  }
  public void arrayStoreWriteBarrier(VM_Address ref, int index, VM_Address value) {
  }
  public void arrayCopyWriteBarrier(VM_Address ref, int startIndex, int endIndex) {
  }
  public void arrayCopyRefCountWriteBarrier(VM_Address src, VM_Address tgt) 
  {
  }

  /**
   * Perform a read barrier operation of the getField bytecode.<p> <b>By default do nothing,
   * override if appropriate.</b>
   *
   * @param tgtObj The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgtObj of the field to be read from
   */
  public void getFieldReadBarrier(VM_Address tgtObj, int offset) {}

  /**
   * Perform a read barrier operation for the getStatic bytecode.<p> <b>By default do nothing,
   * override if appropriate.</b>
   *
   * @param tgtObj The address of the object containing the pointer
   * about to be read
   * @param offset The offset from tgtObj of the field to be read from
   */
  public void getStaticReadBarrier(int staticOffset) {}

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  // abstract static public VM_Address traceObject(VM_Address obj);

  /**
   * Answers true if the given object will not move during this GC
   * or else has already moved.
   *
   * @param obj The object reference whose movability status must be answered.
   * @return whether object has moved or will not move.
   */
  // abstract static public boolean hasMoved(VM_Address obj);


  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param objLoc The location containing the object reference to be traced.  
   *    The object reference is <i>NOT</i> an interior pointer.
   * @return void
   */
  final static public void traceObjectLocation(VM_Address objLoc, boolean root)
    throws VM_PragmaInline {
    VM_Address obj = VM_Magic.getMemoryAddress(objLoc);
    VM_Address newObj = Plan.traceObject(obj, root);
    VM_Magic.setMemoryAddress(objLoc, newObj);
  }
  final static public void traceObjectLocation(VM_Address objLoc)
    throws VM_PragmaInline {
    traceObjectLocation(objLoc, false);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @param interiorRef The interior reference inside obj that must be traced.
   * @return The possibly moved interior reference.
   */
  final static public VM_Address traceInteriorReference(VM_Address obj,
							VM_Address interiorRef,
							boolean root) {
    VM_Offset offset = interiorRef.diff(obj);
    VM_Address newObj = Plan.traceObject(obj, root);
    if (VM.VerifyAssertions) {
      if (offset.toInt() > (1<<24)) {  // There is probably no object this large
	VM.sysWriteln("ERROR: Suspciously large delta of interior pointer from object base");
	VM.sysWriteln("       object base = ", obj);
	VM.sysWriteln("       interior reference = ", interiorRef);
	VM.sysWriteln("       delta = ", offset.toInt());
	VM._assert(false);
      }
    }
    return newObj.add(offset);
  }

}
