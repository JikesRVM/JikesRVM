/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Statistics;


import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * This class implements "trial deletion" cyclic garbage collection
 * using the algorithm described by Bacon and Rajan.<p>
 *
 * Note that the current implementation is <i>not</i> concurrent.<p>
 *
 * See D.F. Bacon and V.T. Rajan, "Concurrent Cycle Collection in
 * Reference Counted Systems", ECOOP, June 2001, LNCS vol 2072.  Note
 * that this has its roots in, but is an improvement over "Lins'
 * algorithm" described in Jones & Lins.<p>
 *
 * Note that there appears to be an error in their encoding of
 * MarkRoots which allows it to over-zealously free a grey object with
 * a RC of zero which is also unprocessed in the root set.  I believe
 * the correct encoding is as follows:<p>
 *
 * <pre>
 *  MarkRoots()
 *    for S in Roots
 *      if (color(S) == purple)
 *        if (RC(S) > 0)
 *          MarkGray(S)
 *        else
 *          Free(S)
 *      else
 *        buffered(S) = false
 *        remove S from Roots
 *</pre>
 *
 * Aside from the use of queues to avoid deep recursion, the following
 * closely mirrors the encoding of the above algorithm that appears in
 * Fig 2 of that paper.<p>
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class TrialDeletion extends CycleDetector
  implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static SharedQueue workPool;
  private static SharedQueue blackPool;
  private static SharedQueue unfilteredPurplePool;
  private static SharedQueue maturePurplePool;
  private static SharedQueue filteredPurplePool;
  private static SharedQueue cyclePoolA;
  private static SharedQueue cyclePoolB;
  private static SharedQueue freePool;

  private static boolean purpleBufferAisOpen = true;

  private static int lastPurplePages;

  private static final int  MARK_GREY = 0;
  private static final int       SCAN = 1;
  private static final int SCAN_BLACK = 2;
  private static final int    COLLECT = 3;

  // filtering should not take more than 1/500 of the time cap
  private static final int FILTER_TIME_FACTOR = 500;
  // granularity at which mark grey traversals should check time cap
  private static final int GREY_VISIT_BOUND = 10;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private RefCountLocal rc;
  private Plan plan;

  private AddressQueue workQueue;
  private AddressQueue blackQueue;
  private AddressQueue unfilteredPurpleBuffer;
  private AddressQueue maturePurpleBuffer;
  private AddressQueue filteredPurpleBuffer;
  private AddressQueue cycleBufferA;
  private AddressQueue cycleBufferB;
  private AddressQueue freeBuffer;

  private TDGreyEnumerator greyEnum;
  private TDScanEnumerator scanEnum;
  private TDScanBlackEnumerator scanBlackEnum;
  private TDCollectEnumerator collectEnum;

  private boolean collectedCycles = false;
  private int phase = MARK_GREY;
  private int visitCount = 0;
  private int objectsProcessed = 0;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //
  static {
    workPool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    workPool.newClient();
    blackPool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    blackPool.newClient();
    unfilteredPurplePool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    unfilteredPurplePool.newClient();
    maturePurplePool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    maturePurplePool.newClient();
    filteredPurplePool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    filteredPurplePool.newClient();
    cyclePoolA = new SharedQueue(Plan.getMetaDataRPA(), 1);
    cyclePoolA.newClient();
    cyclePoolB = new SharedQueue(Plan.getMetaDataRPA(), 1);
    cyclePoolB.newClient();
    freePool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    freePool.newClient();
  }

  TrialDeletion(RefCountLocal rc_, Plan plan_) {
    rc = rc_;
    plan = plan_;
    workQueue = new AddressQueue("cycle workqueue", workPool);
    blackQueue = new AddressQueue("cycle black workqueue", blackPool);
    unfilteredPurpleBuffer = new AddressQueue("unfiltered purple buf", unfilteredPurplePool);
    maturePurpleBuffer = new AddressQueue("mature purple buf", maturePurplePool);
    filteredPurpleBuffer = new AddressQueue("filtered purple buf", filteredPurplePool);
    cycleBufferA = new AddressQueue("cycle buf A", cyclePoolA);
    cycleBufferB = new AddressQueue("cycle buf B", cyclePoolB);
    freeBuffer = new AddressQueue("free buffer", freePool);

    greyEnum = new TDGreyEnumerator(this);
    scanEnum = new TDScanEnumerator(this);
    scanBlackEnum = new TDScanBlackEnumerator(this);
    collectEnum = new TDCollectEnumerator(this);
  }

  final void possibleCycleRoot(VM_Address object)
    throws VM_PragmaInline {
    unfilteredPurpleBuffer.insert(object);
  }

  final boolean collectCycles(boolean time) {
    collectedCycles = false;
    if (shouldFilterPurple()) {
      filterPurpleBufs();
      processFreeBufs();
      if (shouldCollectCycles()) {
	double filterStart = VM_Interface.now();
	double cycleStart = VM_Interface.now();
	double filterTime = cycleStart - filterStart;
	double filterLimit = ((double)Options.gcTimeCap)/FILTER_TIME_FACTOR;
	if ((cycleStart < Plan.getTimeCap()) || (filterTime > filterLimit)) {
	  collectedCycles = true;
	  double remaining = Plan.getTimeCap() - cycleStart;
	  double start = 0;
	  if (Options.verbose > 0) { 
	    start = cycleStart; VM_Interface.sysWrite("(CD "); 
	  }
	  filterMaturePurpleBufs();
	  if (time) Statistics.cdGreyTime.start();
	  doMarkGreyPhase(cycleStart + (remaining/2), filteredPurpleBuffer); // grey phase => 1/2 of remaining
	  if (time) Statistics.cdGreyTime.stop();
	  if (time) Statistics.cdScanTime.start();
	  doScanPhase();
	  if (time) Statistics.cdScanTime.stop();
	  if (time) Statistics.cdCollectTime.start();
	  doCollectPhase();
	  if (time) Statistics.cdCollectTime.stop();
	  if (time) Statistics.cdFreeTime.start();
	  processFreeBufs();
	  if (time) Statistics.cdFreeTime.stop();
	  flushFilteredPurpleBufs();
	  if (Options.verbose > 0) {
	    VM_Interface.sysWrite((VM_Interface.now() - cycleStart)*1000);
	    VM_Interface.sysWrite(" ms)");
	  }
	}
      }
    }
    lastPurplePages = Plan.getMetaDataPagesUsed();
    return collectedCycles;
  }

  private final double timePhase(double start, String phase) {
    double end = VM_Interface.now();
    VM_Interface.sysWrite(phase); VM_Interface.sysWrite(" ");
    VM_Interface.sysWrite((end - start)*1000); VM_Interface.sysWrite(" ms ");
    return end;
  }

  /**
   * Decide whether cycle collection should be invoked.  This uses
   * a probabalisitic heuristic based on heap fullness.
   *
   * @return True if cycle collection should be invoked
   */
  private final boolean shouldCollectCycles() {
    final int LOG_WRIGGLE = 2;
    int slack = log2((int) (Plan.getPagesAvail()<<LOG_WRIGGLE)/Options.cycleDetectionPages);
    int mask = (1<<slack)-1;
    return (slack <= LOG_WRIGGLE) && ((Plan.gcCount() & mask) == mask);
  }

  private final int log2(int value) {
    int rtn = 0;
    while (value > 1<<rtn) rtn++;
    return rtn;
  }

  /**
   * Decide whether the purple buffer should be filtered.  This will
   * happen if the heap is close to full or if the number of purple
   * objects enqued has reached a user-defined threashold.
   *
   * @return True if the unfiltered purple buffer should be filtered
   */
  private final boolean shouldFilterPurple() {
    return 
      (unfilteredPurplePool.enqueuedPages() > Options.cycleMetaDataPages) ||
      (Plan.getPagesAvail() < Options.cycleDetectionPages);
  }


  private final void filterPurpleBufs() {
    VM_Address obj = VM_Address.zero();
    double tc = Plan.getTimeCap();
    double remaining =  tc - VM_Interface.now();
    double timeLimit = tc - (remaining/4);
    int purpleLimit = Options.cycleMetaDataPages>>(LOG_WORD_SIZE+2); // 1/4
    final int FILTER_QUANTA = 2000;
    int purple = 0;
    unfilteredPurpleBuffer.flushLocal();
    do {
      int count = 0;
      while (count < FILTER_QUANTA && 
	     !(obj = unfilteredPurpleBuffer.pop()).isZero()) {
	filter(obj, maturePurpleBuffer);
	count++;
      }
      purple += count;
    } while (!obj.isZero() && VM_Interface.now() < timeLimit &&
	     purple < purpleLimit);
    rc.setPurpleCounter(purple);
  }

  private final void filterMaturePurpleBufs() {
    int count = 0;
    int limit = (objectsProcessed == 0) ? 64<<10 : (int) (1.5 * objectsProcessed);
    VM_Address obj = VM_Address.zero();
    maturePurpleBuffer.flushLocal();
    while (count < limit && !(obj = maturePurpleBuffer.pop()).isZero()) {
      filter(obj, filteredPurpleBuffer);
      count++;
    }
    filteredPurpleBuffer.flushLocal();
  }

  private final void flushFilteredPurpleBufs() {
    VM_Address obj = VM_Address.zero();
    while (!(obj = filteredPurpleBuffer.pop()).isZero()) {
      maturePurpleBuffer.push(obj);
    }
  }

  private final void filter(VM_Address obj, AddressQueue tgt) {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(!RCBaseHeader.isGreen(obj));
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(RCBaseHeader.isBuffered(obj));
    if (RCBaseHeader.isLiveRC(obj)) {
      if (RCBaseHeader.isPurple(obj)) {
	tgt.insert(obj);
      } else {
	RCBaseHeader.clearBufferedBit(obj);
      }
    } else {
      RCBaseHeader.clearBufferedBit(obj);
      freeBuffer.push(obj);
    }
  }

  private final void processFreeBufs() {
    VM_Address obj;
    while (!(obj = freeBuffer.pop()).isZero()) {
      rc.free(obj);
    }
  }

  /****************************************************************************
   *
   * Mark grey
   *
   * Trace from purple "roots", marking grey.  Try to work within a
   * time cap.  This will work <b>only</b> if the purple objects are
   * maintained as a <b>queue</b> rather than a <b>stack</b>
   * (otherwise objects at the bottom of the stack, which may be key,
   * may never get processed).  It is therefore important that the
   * "insert" operation is used when adding to the purple queue,
   * rather than "push".
   *
   */

  /**
   * Vist as many purple objects as time allows and transitively mark
   * grey. This means "pretending" that the initial object is dead,
   * and thus applying temporary decrements to each of the object's
   * decendents.
   *
   * @param markGreyTimeCap The time by which we must stop marking
   * grey.
   * @param src The source of purple objects which are to be marked
   * grey.
   */
  private final void doMarkGreyPhase(double markGreyTimeCap, 
				     AddressQueue src) {
    VM_Address obj = VM_Address.zero();
    AddressQueue tgt = cycleBufferA;
    phase = MARK_GREY;
    do {
      visitCount = 0;
      while (visitCount < GREY_VISIT_BOUND && !(obj = src.pop()).isZero())
	processGreyObject(obj, tgt);
    } while (!obj.isZero() && VM_Interface.now() < markGreyTimeCap);
  }
  private final void processGreyObject(VM_Address object, AddressQueue tgt)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(!RCBaseHeader.isGreen(object));
    if (RCBaseHeader.isPurple(object)) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(RCBaseHeader.isLiveRC(object));
      markGrey(object);
      tgt.push(object);
    } else {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(RCBaseHeader.isGrey(object));
      RCBaseHeader.clearBufferedBit(object);
    }
  }
  private final void markGrey(VM_Address object)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(!RCBaseHeader.isGreen(object));
      visitCount++;
      if (!RCBaseHeader.isGrey(object)) {
	RCBaseHeader.makeGrey(object);
	ScanObject.enumeratePointers(object, greyEnum);
      }
      object = workQueue.pop();
    }
  }
  final void enumerateGrey(VM_Address object)
    throws VM_PragmaInline {
    if (Plan.isRCObject(object) && !RCBaseHeader.isGreen(object)) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(RCBaseHeader.isLiveRC(object));
      RCBaseHeader.decRC(object, false);
      workQueue.push(object);
    }
  }

  private final void doScanPhase() {
    VM_Address object;
    AddressQueue src = cycleBufferA;
    AddressQueue tgt = cycleBufferB;
    phase = SCAN;
    while (!(object = src.pop()).isZero()) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(!RCBaseHeader.isGreen(object));
      scan(object);
      tgt.push(object);
    }
  }
  private final void scan(VM_Address object)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(!RCBaseHeader.isGreen(object));
      if (RCBaseHeader.isGrey(object)) {
	if (RCBaseHeader.isLiveRC(object)) {
	  phase = SCAN_BLACK;
	  scanBlack(object);
	  phase = SCAN;
	} else {
	  RCBaseHeader.makeWhite(object);
	  ScanObject.enumeratePointers(object, scanEnum);
	}
      } 
      object = workQueue.pop();
    }
  }
  final void enumerateScan(VM_Address object) 
    throws VM_PragmaInline {
    if (Plan.isRCObject(object) && !RCBaseHeader.isGreen(object))
      workQueue.push(object);
  }
  private final void scanBlack(VM_Address object)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(blackQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(!RCBaseHeader.isGreen(object));
      //      if (!SimpleRCBaseHeader.isGreen(object)) {
      if (!RCBaseHeader.isBlack(object)) {  // FIXME can't this just be if (isGrey(object)) ??
	RCBaseHeader.makeBlack(object);
	ScanObject.enumeratePointers(object, scanBlackEnum);
      }
      object = blackQueue.pop();
    }
  }
  final void enumerateScanBlack(VM_Address object)
    throws VM_PragmaInline {
    if (Plan.isRCObject(object) && !RCBaseHeader.isGreen(object)) {
      RCBaseHeader.incRC(object, false);
      if (!RCBaseHeader.isBlack(object))
	blackQueue.push(object);
    }
  }

  private final void doCollectPhase() {
    VM_Address object;
    AddressQueue src = cycleBufferB;
    phase = COLLECT;
    while (!(object = src.pop()).isZero()) {
      if (VM_Interface.VerifyAssertions)
	VM_Interface._assert(!RCBaseHeader.isGreen(object));
      RCBaseHeader.clearBufferedBit(object);
      collectWhite(object);
    }
  }
  private final void collectWhite(VM_Address object)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (RCBaseHeader.isWhite(object) && !RCBaseHeader.isBuffered(object)) {
	RCBaseHeader.makeBlack(object);
	ScanObject.enumeratePointers(object, collectEnum);
	freeBuffer.push(object);
      }
      object = workQueue.pop();
    }
  }
  final void enumerateCollect(VM_Address object) 
    throws VM_PragmaInline {
    if (Plan.isRCObject(object)) {
      if (RCBaseHeader.isGreen(object))
	plan.addToDecBuf(object); 
      else
	workQueue.push(object);
    }
  }

  void resetVisitCount() {
    visitCount = 0;
  }
  int getVisitCount() throws VM_PragmaInline {
    return visitCount;
  }
  final void printTimes(boolean totals) {
    double time;
    if (collectedCycles) {
      time = (totals) ? Statistics.cdGreyTime.sum() : Statistics.cdGreyTime.lastMs();
      VM_Interface.sysWrite(" grey: "); VM_Interface.sysWrite(time);
      time = (totals) ? Statistics.cdScanTime.sum() : Statistics.cdScanTime.lastMs();
      VM_Interface.sysWrite(" scan: "); VM_Interface.sysWrite(time);
      time = (totals) ? Statistics.cdCollectTime.sum() : Statistics.cdCollectTime.lastMs();
      VM_Interface.sysWrite(" coll: "); VM_Interface.sysWrite(time);
      time = (totals) ? Statistics.cdFreeTime.sum() : Statistics.cdFreeTime.lastMs();
      VM_Interface.sysWrite(" free: "); VM_Interface.sysWrite(time);
    }
  }
}
