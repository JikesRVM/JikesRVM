/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Time;
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
final class TrialDeletion 
  implements CycleDetector, Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  private static SharedQueue workPool;
  private static SharedQueue blackPool;
  private static SharedQueue purplePoolA;
  private static SharedQueue purplePoolB;
  private static SharedQueue cyclePoolA;
  private static SharedQueue cyclePoolB;
  private static SharedQueue freePool;

  private static boolean purpleBufferAisOpen = true;

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
  private AddressQueue purpleBufferA;
  private AddressQueue purpleBufferB;
  private AddressQueue cycleBufferA;
  private AddressQueue cycleBufferB;
  private AddressQueue freeBuffer;

  private int phase = MARK_GREY;
  private int visitCount = 0;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //
  static {
    workPool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    workPool.newClient();
    blackPool = new SharedQueue(Plan.getMetaDataRPA(), 1);
    blackPool.newClient();
    purplePoolA = new SharedQueue(Plan.getMetaDataRPA(), 1);
    purplePoolA.newClient();
    purplePoolB = new SharedQueue(Plan.getMetaDataRPA(), 1);
    purplePoolB.newClient();
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
    purpleBufferA = new AddressQueue("purple buf A", purplePoolA);
    purpleBufferB = new AddressQueue("purple buf B", purplePoolB);
    cycleBufferA = new AddressQueue("cycle buf A", cyclePoolA);
    cycleBufferB = new AddressQueue("cycle buf B", cyclePoolB);
    freeBuffer = new AddressQueue("free buffer", freePool);
  }

  public final void collectCycles() {
    double filterStart = VM_Time.now();
    filterPurpleBufs();
    processFreeBufs();
    if ((Plan.getPagesAvail() < Options.cycleDetectionPages) ||
	(Plan.getMetaDataPagesUsed() > Options.cycleMetaDataPages) ||
	(Plan.getMetaDataPagesUsed() > (0.8 * Options.metaDataPages))) {
      double cycleStart = VM_Time.now();
      double filterTime = cycleStart - filterStart;
      double filterLimit = ((double)Options.gcTimeCap)/FILTER_TIME_FACTOR;
      if ((cycleStart < Plan.getTimeCap()) || (filterTime > filterLimit)) {
	double remaining = Plan.getTimeCap() - cycleStart;
	double start = 0;
	if (Plan.verbose > 0) { start = cycleStart; VM.sysWrite("(CD "); }
	doMarkGreyPhase(cycleStart + (remaining/3)); // grey phase => 1/3 of remaining
	if (Plan.verbose > 0) start = timePhase(start, "G");
	doScanPhase();
	if (Plan.verbose > 0) start = timePhase(start, "S");
	doCollectPhase();
	if (Plan.verbose > 0) start = timePhase(start, "C");
	processFreeBufs();
	if (Plan.verbose > 0) start = timePhase(start, "F");
	if (Plan.verbose > 0) {
	  VM.sysWrite("= ");
	  VM.sysWrite((VM_Time.now() - cycleStart)*1000);
	  VM.sysWrite(" ms)");
	}
      }
    }
  }
 
  private final double timePhase(double start, String phase) {
    double end = VM_Time.now();
    VM.sysWrite(phase); VM.sysWrite(" ");
    VM.sysWrite((end - start)*1000); VM.sysWrite(" ms ");
    return end;
  }

  public final void possibleCycleRoot(VM_Address object)
    throws VM_PragmaInline {
    if (RCBaseHeader.makePurple(object)) {
      if (purpleBufferAisOpen)
	purpleBufferA.push(object);
      else
	purpleBufferB.push(object);
    }
  }

  private final void filterPurpleBufs() {
    VM_Address obj;
    AddressQueue src = (purpleBufferAisOpen) ? purpleBufferA : purpleBufferB;
    AddressQueue tgt = (purpleBufferAisOpen) ? purpleBufferB : purpleBufferA;
    int purple = 0;
    while (!(obj = src.pop()).isZero()) {
      purple++;
      if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(obj));
      if (VM.VerifyAssertions) VM._assert(RCBaseHeader.isBuffered(obj));
      if (RCBaseHeader.isLiveRC(VM_Magic.addressAsObject(obj))) {
	if (RCBaseHeader.isPurple(VM_Magic.addressAsObject(obj)))
	  tgt.push(obj);
	else {
	  RCBaseHeader.clearBufferedBit(VM_Magic.addressAsObject(obj));
	}
      } else {
	RCBaseHeader.clearBufferedBit(VM_Magic.addressAsObject(obj));
	freeBuffer.push(obj);
      }
    }
    purpleBufferAisOpen = !purpleBufferAisOpen;
    rc.setPurpleCounter(purple);
  }

  private final void processFreeBufs() {
    VM_Address obj;
    while (!(obj = freeBuffer.pop()).isZero()) {
      rc.free(obj);
    }
  }

  private final void doMarkGreyPhase(double markGreyTimeCap) {
    VM_Address obj = VM_Address.zero();
    AddressQueue src = (purpleBufferAisOpen) ? purpleBufferA : purpleBufferB;
    AddressQueue tgt = cycleBufferA;
    phase = MARK_GREY;
    do {
      visitCount = 0;
      while (visitCount < GREY_VISIT_BOUND && !(obj = src.pop()).isZero())
	processGreyObject(obj, tgt);
    } while (!obj.isZero() && VM_Time.now() < markGreyTimeCap);
  }

  private final void processGreyObject(VM_Address object, AddressQueue tgt)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(object));
    if (RCBaseHeader.isPurple(object)) {
      if (VM.VerifyAssertions) VM._assert(RCBaseHeader.isLiveRC(object));
      markGrey(object);
      tgt.push(object);
    } else {
      if (VM.VerifyAssertions) VM._assert(RCBaseHeader.isGrey(object));
      RCBaseHeader.clearBufferedBit(object); // FIXME Why? Why not above?
    }
  }

  private final void doScanPhase() {
    VM_Address object;
    AddressQueue src = cycleBufferA;
    AddressQueue tgt = cycleBufferB;
    phase = SCAN;
    while (!(object = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(object));
      scan(object);
      tgt.push(object);
    }
  }

  private final void doCollectPhase() {
    VM_Address object;
    AddressQueue src = cycleBufferB;
    phase = COLLECT;
    while (!(object = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(object));
      RCBaseHeader.clearBufferedBit(object);
      collectWhite(object);
    }
  }

  public final void enumeratePointer(VM_Address object)
    throws VM_PragmaInline {
    switch (phase) {
    case MARK_GREY: 
      if (!RCBaseHeader.isGreen(object)) {
	if (VM.VerifyAssertions) VM._assert(RCBaseHeader.isLiveRC(object));
	RCBaseHeader.decRC(object);
	workQueue.push(object);
      }
      break;
    case SCAN: 
      if (!RCBaseHeader.isGreen(object))
	workQueue.push(object);
      break;
    case SCAN_BLACK: 
      if (!RCBaseHeader.isGreen(object)) {
	RCBaseHeader.incRC(object);
	if (!RCBaseHeader.isBlack(object))
	  blackQueue.push(object);
      }
      break;
    case COLLECT:  
      if (RCBaseHeader.isGreen(object))
	plan.addToDecBuf(object); 
      else
	workQueue.push(object);
      break;
    default:
      if (VM.VerifyAssertions) VM._assert(false);
    }
  }

  private final void markGrey(VM_Address object)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(object));
      visitCount++;
      if (!RCBaseHeader.isGrey(object)) {
	RCBaseHeader.makeGrey(object);
	ScanObject.enumeratePointers(object, plan);
      }
      object = workQueue.pop();
    }
  }
  private final void scan(VM_Address object)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(object));
      if (RCBaseHeader.isGrey(object)) {
	if (RCBaseHeader.isLiveRC(object)) {
	  phase = SCAN_BLACK;
	  scanBlack(object);
	  phase = SCAN;
	} else {
	  RCBaseHeader.makeWhite(object);
	  ScanObject.enumeratePointers(object, plan);
	}
      } 
      object = workQueue.pop();
    }
  }
  private final void scanBlack(VM_Address object)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(blackQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM.VerifyAssertions) VM._assert(!RCBaseHeader.isGreen(object));
      //      if (!SimpleRCBaseHeader.isGreen(object)) {
      if (!RCBaseHeader.isBlack(object)) {  // FIXME can't this just be if (isGrey(object)) ??
	RCBaseHeader.makeBlack(object);
	ScanObject.enumeratePointers(object, plan);
      }
      object = blackQueue.pop();
    }
  }
  private final void collectWhite(VM_Address object)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (RCBaseHeader.isWhite(object) && !RCBaseHeader.isBuffered(object)) {
	RCBaseHeader.makeBlack(object);
	ScanObject.enumeratePointers(object, plan);
	freeBuffer.push(object);
      }
      object = workQueue.pop();
    }
  }
  public void resetVisitCount() {
    visitCount = 0;
  }
  public int getVisitCount() throws VM_PragmaInline {
    return visitCount;
  }
}
