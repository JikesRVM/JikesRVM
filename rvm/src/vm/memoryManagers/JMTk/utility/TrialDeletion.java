/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.ScanObject;


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
  private static SharedQueue maturePurplePoolA;
  private static SharedQueue maturePurplePoolB;
  private static SharedQueue cyclePoolA;
  private static SharedQueue cyclePoolB;
  private static SharedQueue freePool;

  private static boolean purpleBufferAisOpen = true;
  private static boolean maturePurpleBufferAisOpen = true;

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
  private AddressQueue maturePurpleBufferA;
  private AddressQueue maturePurpleBufferB;
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
    maturePurplePoolA = new SharedQueue(Plan.getMetaDataRPA(), 1);
    maturePurplePoolA.newClient();
    maturePurplePoolB = new SharedQueue(Plan.getMetaDataRPA(), 1);
    maturePurplePoolB.newClient();
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
    maturePurpleBufferA = new AddressQueue("mature purple buf A", maturePurplePoolA);
    maturePurpleBufferB = new AddressQueue("mature purple buf B", maturePurplePoolB);
    cycleBufferA = new AddressQueue("cycle buf A", cyclePoolA);
    cycleBufferB = new AddressQueue("cycle buf B", cyclePoolB);
    freeBuffer = new AddressQueue("free buffer", freePool);
  }

  public final void collectCycles() {
    double filterStart = VM_Interface.now();
    filterPurpleBufs();
    processFreeBufs();
    if (collectCycles(false)) {
      double cycleStart = VM_Interface.now();
      double filterTime = cycleStart - filterStart;
      double filterLimit = ((double)Options.gcTimeCap)/FILTER_TIME_FACTOR;
      if ((cycleStart < Plan.getTimeCap()) || (filterTime > filterLimit)) {
	double remaining = Plan.getTimeCap() - cycleStart;
	double start = 0;
	if (Plan.verbose > 0) { 
	  start = cycleStart; VM_Interface.sysWrite("(CD "); 
	}
	doMarkGreyPhase(cycleStart + (remaining/2), (purpleBufferAisOpen) ? purpleBufferA : purpleBufferB); // grey phase => 1/2 of remaining

	if (collectCycles(true)) {
	  remaining = Plan.getTimeCap() - cycleStart;
// 	  if (Plan.verbose > 0) {
// 	    if (Plan.getPagesAvail() < Options.cycleDetectionPages)
// 	      VM_Interface.sysWrite("P");
// 	    if (Plan.getMetaDataPagesUsed() > Options.cycleMetaDataPages)
// 	      VM_Interface.sysWrite("C");
// 	    if (Plan.getMetaDataPagesUsed() > (0.8 * Options.metaDataPages))
// 	      VM_Interface.sysWrite("M");
// 	    VM_Interface.sysWrite("* ");
// 	  }
	  doMarkGreyPhase(cycleStart + (remaining/2), (maturePurpleBufferAisOpen) ? maturePurpleBufferA : maturePurpleBufferB); // grey phase => 1/2 of remaining
	}
	if (Plan.verbose > 0) start = timePhase(start, "G");
	doScanPhase();
	if (Plan.verbose > 0) start = timePhase(start, "S");
	doCollectPhase();
	if (Plan.verbose > 0) start = timePhase(start, "C");
	processFreeBufs();
	if (Plan.verbose > 0) start = timePhase(start, "F");
	if (Plan.verbose > 0) {
	  VM_Interface.sysWrite("= ");
	  VM_Interface.sysWrite((VM_Interface.now() - cycleStart)*1000);
	  VM_Interface.sysWrite(" ms)");
	}
      }
    }
  }

  private final boolean collectCycles(boolean fullHeap) {
    boolean major, minor;
    major = ((Plan.getPagesAvail() < Options.cycleDetectionPages) ||
	     (Plan.getMetaDataPagesUsed() > Options.cycleMetaDataPages) ||
	     (Plan.getMetaDataPagesUsed() > (0.8 * Options.metaDataPages)));
    

    if (Options.genCycleDetection) {
      minor = ((Plan.getMetaDataPagesUsed() > Options.cycleMetaDataPages/2) ||
	       (Plan.getMetaDataPagesUsed() > (0.4 * Options.metaDataPages)));
      if (fullHeap)
	return major;
      else
	return minor;
    } else {
      if (fullHeap)
	return false;
      else
	return major;
    }
  }

  private final double timePhase(double start, String phase) {
    double end = VM_Interface.now();
    VM_Interface.sysWrite(phase); VM_Interface.sysWrite(" ");
    VM_Interface.sysWrite((end - start)*1000); VM_Interface.sysWrite(" ms ");
    return end;
  }

  public final void possibleCycleRoot(VM_Address object)
    throws VM_PragmaInline {
    if (RCBaseHeader.makePurple(object)) {
      if (purpleBufferAisOpen)
	purpleBufferA.insert(object);
      else
	purpleBufferB.insert(object);
    }
  }

  private final void filterPurpleBufs() {
    AddressQueue src = (purpleBufferAisOpen) ? purpleBufferA : purpleBufferB;
    AddressQueue tgt = (purpleBufferAisOpen) ? purpleBufferB : purpleBufferA;
    AddressQueue matSrc = (maturePurpleBufferAisOpen) ? maturePurpleBufferA : maturePurpleBufferB;
    AddressQueue matTgt = (maturePurpleBufferAisOpen) ? maturePurpleBufferB : maturePurpleBufferA;
    if (Options.genCycleDetection) {
      filter(false, matSrc, matTgt, null);
      maturePurpleBufferAisOpen = !maturePurpleBufferAisOpen;
      filter(true, src, tgt, matTgt);
    } else
      filter(false, src, tgt, null);
    purpleBufferAisOpen = !purpleBufferAisOpen;
  }

  private final void filter(boolean nursery, AddressQueue src,
			    AddressQueue tgt, AddressQueue mature) {
    VM_Address obj;
    int purple = 0;
    int x = 0;
    src.flushLocal();
    while (!(obj = src.pop()).isZero()) {
      purple++;
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(obj));
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(RCBaseHeader.isBuffered(obj));
      if (RCBaseHeader.isLiveRC(obj)) {
	if (RCBaseHeader.isPurple(obj)) {
 	  if (nursery && RCBaseHeader.isMature(obj)) {
 	    mature.insert(obj);
 	  } else
	    tgt.insert(obj);
	} else {
	  RCBaseHeader.clearBufferedBit(obj);
	}
      } else {
	RCBaseHeader.clearBufferedBit(obj);
	freeBuffer.push(obj);
      }
    }
    //    VM_Interface.sysWrite("("); VM_Interface.sysWrite(x); VM_Interface.sysWrite(" ");VM_Interface.sysWrite(purple); VM_Interface.sysWrite((nursery ? "P)" : "MP)"));
    tgt.flushLocal();
    rc.setPurpleCounter(purple);
  }

  private final void processFreeBufs() {
    VM_Address obj;
    while (!(obj = freeBuffer.pop()).isZero()) {
      rc.free(obj);
    }
  }

  /**
   * Trace from purple "roots", marking grey.  Try to work within a
   * time cap.  This will work <b>only</b> if the purple objects are
   * maintained as a <b>queue</b> rather than a <b>stack</b>
   * (otherwise objects at the bottom of the stack, which may be key,
   * may never get processed).  It is therefore important that the
   * "insert" operation is used when adding to the purple queue,
   * rather than "push".
   *
   * @param markGreyTimeCap
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(object));
    if (RCBaseHeader.isPurple(object)) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(RCBaseHeader.isLiveRC(object));
      markGrey(object);
      tgt.push(object);
    } else {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(RCBaseHeader.isGrey(object));
      RCBaseHeader.clearBufferedBit(object); // FIXME Why? Why not above?
    }
  }

  private final void doScanPhase() {
    VM_Address object;
    AddressQueue src = cycleBufferA;
    AddressQueue tgt = cycleBufferB;
    phase = SCAN;
    while (!(object = src.pop()).isZero()) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(object));
      scan(object);
      tgt.push(object);
    }
  }

  private final void doCollectPhase() {
    VM_Address object;
    AddressQueue src = cycleBufferB;
    phase = COLLECT;
    while (!(object = src.pop()).isZero()) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(object));
      RCBaseHeader.clearBufferedBit(object);
      collectWhite(object);
    }
  }

  public final void enumeratePointer(VM_Address object)
    throws VM_PragmaInline {
    switch (phase) {
    case MARK_GREY: 
      if (!RCBaseHeader.isGreen(object)) {
	if (VM_Interface.VerifyAssertions) VM_Interface._assert(RCBaseHeader.isLiveRC(object));
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
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    }
  }

  private final void markGrey(VM_Address object)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(object));
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(workQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(object));
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(blackQueue.pop().isZero());
    while (!object.isZero()) {
      if (VM_Interface.VerifyAssertions) VM_Interface._assert(!RCBaseHeader.isGreen(object));
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
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(workQueue.pop().isZero());
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
