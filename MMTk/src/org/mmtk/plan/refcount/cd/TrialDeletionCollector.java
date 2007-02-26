/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.refcount.cd;

import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.utility.Constants;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.utility.scan.Scan;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for a trial deletion cycle detector.
 * 
 * $Id$
 * 
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
@Uninterruptible public final class TrialDeletionCollector extends CDCollector implements Constants {

  /****************************************************************************
   * 
   * Class variables
   */
  
  /****************************************************************************
   * 
   * Instance variables
   */
  public ObjectReferenceDeque workQueue;
  public ObjectReferenceDeque blackQueue;
  public ObjectReferenceDeque unfilteredPurpleBuffer;
  public ObjectReferenceDeque maturePurpleBuffer;
  public ObjectReferenceDeque filteredPurpleBuffer;
  public ObjectReferenceDeque cycleBufferA;
  public ObjectReferenceDeque cycleBufferB;
  public ObjectReferenceDeque freeBuffer;
  
  public TrialDeletionCollectStep collectStep;
  public TrialDeletionGreyStep greyStep;
  public TrialDeletionScanStep scanStep;
  public TrialDeletionScanBlackStep scanBlackStep;

  /****************************************************************************
   * 
   * Initialization
   */
  public TrialDeletionCollector() {
    workQueue = new ObjectReferenceDeque("cycle workqueue", global().workPool);
    global().workPool.newConsumer();
    blackQueue = new ObjectReferenceDeque("cycle black workqueue", global().blackPool);
    global().blackPool.newConsumer();
    unfilteredPurpleBuffer = new ObjectReferenceDeque("unfiltered purple buf", global().unfilteredPurplePool);
    global().unfilteredPurplePool.newConsumer();
    maturePurpleBuffer = new ObjectReferenceDeque("mature purple buf", global().maturePurplePool);
    global().maturePurplePool.newConsumer();
    filteredPurpleBuffer = new ObjectReferenceDeque("filtered purple buf", global().filteredPurplePool);
    global().filteredPurplePool.newConsumer();
    cycleBufferA = new ObjectReferenceDeque("cycle buf A", global().cyclePoolA);
    global().cyclePoolA.newConsumer();
    cycleBufferB = new ObjectReferenceDeque("cycle buf B", global().cyclePoolB);
    global().cyclePoolB.newConsumer();
    freeBuffer = new ObjectReferenceDeque("free buffer", global().freePool);
    global().freePool.newConsumer();
    collectStep = new TrialDeletionCollectStep();
    greyStep = new TrialDeletionGreyStep();
    scanStep = new TrialDeletionScanStep();
    scanBlackStep = new TrialDeletionScanBlackStep();
  }

  /**
   * Perform a collection phase.
   * 
   * @param phaseId Collection phase to execute.
   * @param primary Use this thread to execute any single-threaded collector
   * context actions.
   */
  public final boolean collectionPhase(int phaseId, boolean primary) {
    boolean filter  = global().cdMode >= TrialDeletion.FILTER_PURPLE;
    boolean collect = global().cdMode >= TrialDeletion.FULL_COLLECTION;
    
    // Phases that occur when we are filtering or collecting
    if (phaseId == TrialDeletion.CD_FILTER_PURPLE) {
      if (filter) filterPurpleBufs();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_FREE_FILTERED) {
      if (filter) processFreeBufs();
      return true;
    }
  
    // Phases that occur when we are doing a full collection
    if (phaseId == TrialDeletion.CD_FILTER_MATURE) {
      if (collect) filterMaturePurpleBufs();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_MARK_GREY) {
      if (collect && primary) doMarkGreyPhase();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_SCAN) {
      if (collect && primary) doScanPhase();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_COLLECT) {
      if (collect && primary) doCollectPhase();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_FREE) {
      if (collect) processFreeBufs();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_FLUSH_FILTERED) {
      if (collect) flushFilteredPurpleBufs();
      return true;
    }
    
    if (phaseId == TrialDeletion.CD_PROCESS_DECS) {
      if (collect) ((RCBaseCollector)VM.activePlan.collector()).processDecBuffer();
      return true;
    }
    
    return false;
  }

  private void filterPurpleBufs() {
    filterPurpleBufs(unfilteredPurpleBuffer, maturePurpleBuffer);
    maturePurpleBuffer.flushLocal();
  }
  
  private void filterMaturePurpleBufs() {
    if (filteredPurpleBuffer.isEmpty()) {
      filterPurpleBufs(maturePurpleBuffer, filteredPurpleBuffer);
    }
  }

  private void filterPurpleBufs(ObjectReferenceDeque src, ObjectReferenceDeque tgt) {
    ObjectReference object = ObjectReference.nullReference();
    src.flushLocal();
    while (!(object = src.pop()).isNull()) {
      filter(object, tgt);
    }
    tgt.flushLocal();
  }

  private void flushFilteredPurpleBufs() {
    ObjectReference object = ObjectReference.nullReference();
    while (!(object = filteredPurpleBuffer.pop()).isNull()) {
      maturePurpleBuffer.push(object);
    }
    maturePurpleBuffer.flushLocal();
  }

  private void filter(ObjectReference object, ObjectReferenceDeque tgt) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RCHeader.isBuffered(object));
    if (RCHeader.isLiveRC(object)) {
      if (RCHeader.isPurple(object)) {
        tgt.insert(object);
      } else {
        RCHeader.clearBufferedBit(object);
      }
    } else {
      RCHeader.clearBufferedBit(object);
      freeBuffer.push(object);
    }
  }

  private void processFreeBufs() {
    ObjectReference object;
    while (!(object = freeBuffer.pop()).isNull()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isBuffered(object));
      RCBase.free(object);
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
   */
  private void doMarkGreyPhase() {
    ObjectReference object = ObjectReference.nullReference();
    while (!(object = filteredPurpleBuffer.pop()).isNull()) {
      processGreyObject(object, cycleBufferA);
    }
  }
  @Inline
  private boolean processGreyObject(ObjectReference object,
                                          ObjectReferenceDeque tgt) { 
   // Log.write("pg[");Log.write(object);RefCountSpace.print(object);Log.writeln("]");
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
    if (RCHeader.isPurpleNotGrey(object)) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(RCHeader.isLiveRC(object));
      markGrey(object);
      tgt.push(object);
    } else {
      if (VM.VERIFY_ASSERTIONS) {
        if (!(RCHeader.isGrey(object) || RCHeader.isBlack(object))) {
          RCHeader.print(object);
        }
        VM.assertions._assert(RCHeader.isGrey(object) || RCHeader.isBlack(object));
      }
      RCHeader.clearBufferedBit(object);
    }
    return true;
  }
  
  @Inline
  private void markGrey(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(workQueue.pop().isNull());
    while (!object.isNull()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
      
      if (!RCHeader.isGrey(object)) {
        RCHeader.makeGrey(object);
        Scan.scanObject(greyStep, object);
      }
      object = workQueue.pop();
    }
  }
  
  @Inline
  public final void enumerateGrey(ObjectReference object) { 
    if (RCBase.isRCObject(object) && !RCHeader.isGreen(object)) {
      if (VM.VERIFY_ASSERTIONS) {
        // TODO VM.assertions._assert(RCHeader.isLiveRC(object));
      }
      RCHeader.unsyncDecRC(object);
      workQueue.push(object);
    }
  }

  private void doScanPhase() {
    ObjectReference object;
    ObjectReferenceDeque src = cycleBufferA;
    ObjectReferenceDeque tgt = cycleBufferB;
    while (!(object = src.pop()).isNull()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
      scan(object);
      tgt.push(object);
    }
  }

  @Inline
  private void scan(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(workQueue.pop().isNull());
    while (!object.isNull()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
      if (RCHeader.isGrey(object)) {
        if (RCHeader.isLiveRC(object)) {
          scanBlack(object);
        } else {
          RCHeader.makeWhite(object);
          Scan.scanObject(scanStep, object);
        }
      }
      object = workQueue.pop();
    }
  }
  
  @Inline
  public final void enumerateScan(ObjectReference object) { 
    if (RCBase.isRCObject(object) && !RCHeader.isGreen(object))
      workQueue.push(object);
  }
  
  @Inline
  private void scanBlack(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(blackQueue.pop().isNull());
    while (!object.isNull()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
      if (!RCHeader.isBlack(object)) {  // FIXME can't this just be if (isGrey(object)) ??
        RCHeader.makeBlack(object);
        Scan.scanObject(scanBlackStep, object);
      }
      object = blackQueue.pop();
    }
  }
  @Inline
  public final void enumerateScanBlack(ObjectReference object) { 
    if (RCBase.isRCObject(object) && !RCHeader.isGreen(object)) {
      RCHeader.unsyncIncRC(object);
      if (!RCHeader.isBlack(object))
        blackQueue.push(object);
    }
  }

  private void doCollectPhase() {
    ObjectReference object;
    ObjectReferenceDeque src = cycleBufferB;
    while (!(object = src.pop()).isNull()) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!RCHeader.isGreen(object));
      RCHeader.clearBufferedBit(object);
      collectWhite(object);
    }
  }
  
  @Inline
  private void collectWhite(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(workQueue.pop().isNull());
    while (!object.isNull()) {
      if (RCHeader.isWhite(object) && !RCHeader.isBuffered(object)) {
        RCHeader.makeBlack(object);
        Scan.scanObject(collectStep, object);
        freeBuffer.push(object);
      }
      object = workQueue.pop();
    }
  }
  
  @Inline
  public final void enumerateCollect(ObjectReference object) { 
    if (RCBase.isRCObject(object)) {
      if (RCHeader.isGreen(object)) {
        ((RCBaseCollector)VM.activePlan.collector()).decBuffer.push(object);
      } else {
        workQueue.push(object);
      }
    }
  }
  
  /**
   * Buffer an object after a successful update when shouldBufferOnDecRC
   * returned true.
   *  
   * @param object The object to buffer.
   */
  public void bufferOnDecRC(ObjectReference object) {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(!RCHeader.isGreen(object));
    }
    unfilteredPurpleBuffer.insert(object);
  }

  /** @return The active global plan as an <code>MS</code> instance. */
  @Inline
  private static TrialDeletion global() {
    return (TrialDeletion)((RCBase)VM.activePlan.global()).cycleDetector();
  }
}
