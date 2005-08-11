/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.utility;

import org.mmtk.plan.Plan;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Statistics;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

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
public final class TrialDeletion extends CycleDetector
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  private static SharedDeque workPool;
  private static SharedDeque blackPool;
  private static SharedDeque unfilteredPurplePool;
  private static SharedDeque maturePurplePool;
  private static SharedDeque filteredPurplePool;
  private static SharedDeque cyclePoolA;
  private static SharedDeque cyclePoolB;
  private static SharedDeque freePool;

  private static boolean purpleBufferAisOpen = true;

  private static int lastPurplePages;

  private static final int  MARK_GREY = 0;
  private static final int       SCAN = 1;
  private static final int SCAN_BLACK = 2;
  private static final int    COLLECT = 3;

  private static final int FILTER_TIME_FRACTION = 3;
  private static final int MATURE_FILTER_TIME_FRACTION = 4;
  private static final int CYCLE_TIME_FRACTION = 6;
  private static final int MARK_GREY_TIME_FRACTION = 2;

  // granularity at which mark grey traversals should check time cap
  private static final int GREY_VISIT_BOUND = 10;
  private static final int GREY_VISIT_GRAIN = 100;
  private static final int FILTER_BOUND = 8192;

  // Statistics
  private static Timer greyTime;
  private static Timer scanTime;
  private static Timer whiteTime;
  private static Timer freeTime;

  /****************************************************************************
   *
   * Instance variables
   */
  private RefCountLocal rc;

  private ObjectReferenceDeque workQueue;
  private ObjectReferenceDeque blackQueue;
  private ObjectReferenceDeque unfilteredPurpleBuffer;
  private ObjectReferenceDeque maturePurpleBuffer;
  private ObjectReferenceDeque filteredPurpleBuffer;
  private ObjectReferenceDeque cycleBufferA;
  private ObjectReferenceDeque cycleBufferB;
  private ObjectReferenceDeque freeBuffer;

  private TDGreyEnumerator greyEnum;
  private TDScanEnumerator scanEnum;
  private TDScanBlackEnumerator scanBlackEnum;
  private TDCollectEnumerator collectEnum;

  private boolean collectedCycles = false;
  private int phase = MARK_GREY;
  private int visitCount = 0;
  private int objectsProcessed = 0;

  /****************************************************************************
   *
   * Initialization
   */
  static {
    workPool = new SharedDeque(Plan.metaDataSpace, 1);
    workPool.newClient();
    blackPool = new SharedDeque(Plan.metaDataSpace, 1);
    blackPool.newClient();
    unfilteredPurplePool = new SharedDeque(Plan.metaDataSpace, 1);
    unfilteredPurplePool.newClient();
    maturePurplePool = new SharedDeque(Plan.metaDataSpace, 1);
    maturePurplePool.newClient();
    filteredPurplePool = new SharedDeque(Plan.metaDataSpace, 1);
    filteredPurplePool.newClient();
    cyclePoolA = new SharedDeque(Plan.metaDataSpace, 1);
    cyclePoolA.newClient();
    cyclePoolB = new SharedDeque(Plan.metaDataSpace, 1);
    cyclePoolB.newClient();
    freePool = new SharedDeque(Plan.metaDataSpace, 1);
    freePool.newClient();

    greyTime = new Timer("cd-grey", false, true);
    scanTime = new Timer("cd-scan", false, true);
    whiteTime = new Timer("cd-white", false, true);
    freeTime = new Timer("cd-free", false, true);
  }

  public TrialDeletion(RefCountLocal rc) {
    this.rc = rc;
    workQueue = new ObjectReferenceDeque("cycle workqueue", workPool);
    blackQueue = new ObjectReferenceDeque("cycle black workqueue", blackPool);
    unfilteredPurpleBuffer = new ObjectReferenceDeque("unfiltered purple buf", unfilteredPurplePool);
    maturePurpleBuffer = new ObjectReferenceDeque("mature purple buf", maturePurplePool);
    filteredPurpleBuffer = new ObjectReferenceDeque("filtered purple buf", filteredPurplePool);
    cycleBufferA = new ObjectReferenceDeque("cycle buf A", cyclePoolA);
    cycleBufferB = new ObjectReferenceDeque("cycle buf B", cyclePoolB);
    freeBuffer = new ObjectReferenceDeque("free buffer", freePool);

    greyEnum = new TDGreyEnumerator(this);
    scanEnum = new TDScanEnumerator(this);
    scanBlackEnum = new TDScanBlackEnumerator(this);
    collectEnum = new TDCollectEnumerator(this);
  }

  public final void possibleCycleRoot(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
    //    Log.write("p[");Log.write(object);RefCountSpace.print(object);Log.writeln("]");
    unfilteredPurpleBuffer.insert(object);
  }

  public final boolean collectCycles(boolean primary, boolean timekeeper) {
    collectedCycles = false;
    if (primary && shouldFilterPurple()) {
      long filterStart = Statistics.cycles();
      long finishTarget = Plan.getTimeCap();
      long remaining = finishTarget - filterStart;
      long targetTime = filterStart+(remaining/FILTER_TIME_FRACTION);

      long gcTimeCap = Statistics.millisToCycles(Options.gcTimeCap.getMilliseconds());
      if (remaining > gcTimeCap/FILTER_TIME_FRACTION ||
          RefCountSpace.RC_SANITY_CHECK) {
        filterPurpleBufs(targetTime);
        processFreeBufs();
        if (shouldCollectCycles()) {
          if (Options.verbose.getValue() > 0) { 
            Log.write("(CD "); 
            Log.flush();
          }
          long cycleStart = Statistics.cycles();
          remaining = finishTarget - cycleStart;
          boolean abort = false;
          while (maturePurplePool.enqueuedPages()> 0 && !abort &&
                 (remaining > gcTimeCap/CYCLE_TIME_FRACTION ||
                  RefCountSpace.RC_SANITY_CHECK)) {
            abort = collectSomeCycles(timekeeper, finishTarget);
            remaining = finishTarget - Statistics.cycles();
          }
          flushFilteredPurpleBufs();
          if (Options.verbose.getValue() > 0) {
            Log.write(Statistics.cyclesToMillis(Statistics.cycles() - cycleStart));
            Log.write(" ms)");
          }
        }
      }
      lastPurplePages = ActivePlan.global().getMetaDataPagesUsed();
    }
    return collectedCycles;
  }


  private final boolean collectSomeCycles(boolean timekeeper,
                                          long finishTarget) {
    collectedCycles = true;
    filterMaturePurpleBufs();
    if (timekeeper) greyTime.start();
    long start = Statistics.cycles();
    long remaining = finishTarget - start;
    long targetTime = start + (remaining/MARK_GREY_TIME_FRACTION);
    boolean abort = doMarkGreyPhase(targetTime);
    if (timekeeper) greyTime.stop();
    if (timekeeper) scanTime.start();
    doScanPhase();
    if (timekeeper) scanTime.stop();
    if (timekeeper) whiteTime.start();
    doCollectPhase();
    if (timekeeper) whiteTime.stop();
    if (timekeeper) freeTime.start();
    processFreeBufs();
    if (timekeeper) freeTime.stop();
    return abort;
  }

  private final long timePhase(long start, String phase) {
    long end = Statistics.cycles();
    Log.write(phase); Log.write(" ");
    Log.write(Statistics.cyclesToMillis(end - start)); Log.write(" ms ");
    return end;
  }

  /**
   * Decide whether cycle collection should be invoked.  This uses
   * a probabalisitic heuristic based on heap fullness.
   *
   * @return True if cycle collection should be invoked
   */
  private final boolean shouldCollectCycles() {
    return shouldAct(Options.cycleTriggerThreshold.getPages());
  }

  /**
   * Decide whether the purple buffer should be filtered.  This will
   * happen if the heap is close to full or if the number of purple
   * objects enqued has reached a user-defined threashold.
   *
   * @return True if the unfiltered purple buffer should be filtered
   */
  private final boolean shouldFilterPurple() {
    return shouldAct(Options.cycleFilterThreshold.getPages());
  }

  /**
   * Decide whether to act on cycle collection or purple filtering.
   * This uses a probabalisitic heuristic based on heap fullness.
   *
   * @return True if we should act
   */
  private final boolean shouldAct(int thresholdPages) {
    if (RefCountSpace.RC_SANITY_CHECK) return true;

    final int LOG_WRIGGLE = 2;
    int slack = log2((int) ActivePlan.global().getPagesAvail()/thresholdPages);
    int mask = (1<<slack)-1;
    boolean rtn = (slack <= LOG_WRIGGLE) && ((Stats.gcCount() & mask) == mask);
    return rtn;
  }
  private final int log2(int value) {
    int rtn = 0;
    while (value > 1<<rtn) rtn++;
    return rtn;
  }

  private final void filterPurpleBufs(long timeCap) {
    int p = filterPurpleBufs(unfilteredPurpleBuffer, maturePurpleBuffer,
                             timeCap);
    maturePurpleBuffer.flushLocal();
    rc.setPurpleCounter(p);
  }
  private final void filterMaturePurpleBufs() {
    if (filteredPurplePool.enqueuedPages() == 0) {
      filterPurpleBufs(maturePurpleBuffer, filteredPurpleBuffer,
                       Statistics.cycles());
      filteredPurpleBuffer.flushLocal();
    }
  }

  private final int filterPurpleBufs(ObjectReferenceDeque src, 
                                     ObjectReferenceDeque tgt, long timeCap) {
    int purple = 0;
    int limit = Options.cycleMetaDataLimit.getPages() <<(LOG_BYTES_IN_PAGE-LOG_BYTES_IN_ADDRESS-1);
    ObjectReference object = ObjectReference.nullReference();
    src.flushLocal();
    do {
      int p = 0;
      while (p < FILTER_BOUND && !(object = src.pop()).isNull()) {
        filter(object, tgt);
        p++;
      }
      purple += p;
    } while (!object.isNull() && 
             ((Statistics.cycles() < timeCap && purple < limit) ||
              RefCountSpace.RC_SANITY_CHECK));
    return purple;
  }

  private final void flushFilteredPurpleBufs() {
    ObjectReference object = ObjectReference.nullReference();
    while (!(object = filteredPurpleBuffer.pop()).isNull()) {
      maturePurpleBuffer.push(object);
    }
  }

  private final void filter(ObjectReference object, ObjectReferenceDeque tgt) {
    //    Log.write("f[");Log.write(obj);RefCountSpace.print(obj);Log.writeln("]");
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(RefCountSpace.isBuffered(object));
    if (RefCountSpace.isLiveRC(object)) {
      if (RefCountSpace.isPurple(object)) {
        tgt.insert(object);
      } else {
        RefCountSpace.clearBufferedBit(object);
      }
    } else {
      RefCountSpace.clearBufferedBit(object);
      freeBuffer.push(object);
    }
  }

  private final void processFreeBufs() {
    ObjectReference object;
    while (!(object = freeBuffer.pop()).isNull()) {
      rc.free(object);
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
   * @param timeCap The time by which we must stop marking
   * grey.
   */
  private final boolean doMarkGreyPhase(long timeCap) {
    ObjectReference object = ObjectReference.nullReference();
    boolean abort = false;
    phase = MARK_GREY;
    do {
      visitCount = 0;
      while (visitCount < GREY_VISIT_BOUND && !abort &&
             !(object = filteredPurpleBuffer.pop()).isNull()) {
        if (!processGreyObject(object, cycleBufferA, timeCap)) {
          abort = true;
          maturePurpleBuffer.insert(object);
        }
      }
    } while (!object.isNull() && !abort && 
             ((Statistics.cycles() < timeCap) || 
              RefCountSpace.RC_SANITY_CHECK));
    return abort;
  }
  private final boolean processGreyObject(ObjectReference object,
                                          ObjectReferenceDeque tgt,
                                          long timeCap)
    throws InlinePragma {
    //    Log.write("pg[");Log.write(object);RefCountSpace.print(object);Log.writeln("]");
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
    if (RefCountSpace.isPurpleNotGrey(object)) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(RefCountSpace.isLiveRC(object));
      if (!markGrey(object, timeCap)) {
        scanBlack(object);
        return false;
      } else
        tgt.push(object);
    } else {
      if (Assert.VERIFY_ASSERTIONS) {
        if (!(RefCountSpace.isGrey(object)
              || RefCountSpace.isBlack(object))) {
          RefCountSpace.print(object);
        }
        if (Assert.VERIFY_ASSERTIONS) Assert._assert(RefCountSpace.isGrey(object)
                             || RefCountSpace.isBlack(object));
      }
      RefCountSpace.clearBufferedBit(object);
    }
    return true;
  }
  private final boolean markGrey(ObjectReference object, long timeCap)
    throws InlinePragma {
    boolean abort = false;
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(workQueue.pop().isNull());
    while (!object.isNull()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
      visitCount++;
      if (visitCount % GREY_VISIT_GRAIN == 0 && !RefCountSpace.RC_SANITY_CHECK 
          && Statistics.cycles() > timeCap) {
        abort = true;
      }
      
      if (!abort && !RefCountSpace.isGrey(object)) {
        RefCountSpace.makeGrey(object);
        Scan.enumeratePointers(object, greyEnum);
      }
      object = workQueue.pop();
    }
    return !abort;
  }
  public final void enumerateGrey(ObjectReference object)
    throws InlinePragma {
    if (RCBase.isRCObject(object) && !RefCountSpace.isGreen(object)) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(RefCountSpace.isLiveRC(object));
      RefCountSpace.unsyncDecRC(object);
      workQueue.push(object);
    }
  }

  private final void doScanPhase() {
    ObjectReference object;
    ObjectReferenceDeque src = cycleBufferA;
    ObjectReferenceDeque tgt = cycleBufferB;
    phase = SCAN;
    while (!(object = src.pop()).isNull()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
      scan(object);
      tgt.push(object);
    }
  }

  private final void scan(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(workQueue.pop().isNull());
    while (!object.isNull()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
      if (RefCountSpace.isGrey(object)) {
        if (RefCountSpace.isLiveRC(object)) {
          phase = SCAN_BLACK;
          scanBlack(object);
          phase = SCAN;
        } else {
          RefCountSpace.makeWhite(object);
          Scan.enumeratePointers(object, scanEnum);
        }
      } 
      object = workQueue.pop();
    }
  }
  public final void enumerateScan(ObjectReference object) 
    throws InlinePragma {
    if (RCBase.isRCObject(object) && !RefCountSpace.isGreen(object))
      workQueue.push(object);
  }
  private final void scanBlack(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(blackQueue.pop().isNull());
    while (!object.isNull()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
      if (!RefCountSpace.isBlack(object)) {  // FIXME can't this just be if (isGrey(object)) ??
        RefCountSpace.makeBlack(object);
        Scan.enumeratePointers(object, scanBlackEnum);
      }
      object = blackQueue.pop();
    }
  }
  public final void enumerateScanBlack(ObjectReference object)
    throws InlinePragma {
    if (RCBase.isRCObject(object) && !RefCountSpace.isGreen(object)) {
      RefCountSpace.unsyncIncRC(object);
      if (!RefCountSpace.isBlack(object))
        blackQueue.push(object);
    }
  }

  private final void doCollectPhase() {
    ObjectReference object;
    ObjectReferenceDeque src = cycleBufferB;
    phase = COLLECT;
    while (!(object = src.pop()).isNull()) {
      if (Assert.VERIFY_ASSERTIONS) Assert._assert(!RefCountSpace.isGreen(object));
      RefCountSpace.clearBufferedBit(object);
      collectWhite(object);
    }
  }
  private final void collectWhite(ObjectReference object)
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(workQueue.pop().isNull());
    while (!object.isNull()) {
      if (RefCountSpace.isWhite(object) && !RefCountSpace.isBuffered(object)) {
        RefCountSpace.makeBlack(object);
        Scan.enumeratePointers(object, collectEnum);
        freeBuffer.push(object);
      }
      object = workQueue.pop();
    }
  }
  public final void enumerateCollect(ObjectReference object) 
    throws InlinePragma {
    if (RCBase.isRCObject(object)) {
      if (RefCountSpace.isGreen(object))
        RCBase.local().addToDecBuf(object); 
      else
        workQueue.push(object);
    }
  }

  void resetVisitCount() {
    visitCount = 0;
  }
  int getVisitCount() throws InlinePragma {
    return visitCount;
  }
}
