/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.plan.refcount;

import org.mmtk.plan.Phase;
import org.mmtk.plan.StopTheWorldCollector;
import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TransitiveClosure;
import org.mmtk.plan.refcount.backuptrace.BTTraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.ExplicitFreeListSpace;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.ObjectReference;

/**
 * This class implements the collector context for a reference counting collector.
 * See Shahriyar et al for details of and rationale for the optimizations used
 * here (http://dx.doi.org/10.1145/2258996.2259008).  See Chapter 4 of
 * Daniel Frampton's PhD thesis for details of and rationale for the cycle
 * collection strategy used by this collector.
 */
@Uninterruptible
public abstract class RCBaseCollector extends StopTheWorldCollector {

  /************************************************************************
   * Initialization
   */

  /**
   *
   */
  protected final ObjectReferenceDeque newRootBuffer;
  private final BTTraceLocal backupTrace;
  private final ObjectReferenceDeque modBuffer;
  private final ObjectReferenceDeque oldRootBuffer;
  private final RCDecBuffer decBuffer;
  private final RCZero zero;

  /**
   * Constructor.
   */
  public RCBaseCollector() {
    newRootBuffer = new ObjectReferenceDeque("new-root", global().newRootPool);
    oldRootBuffer = new ObjectReferenceDeque("old-root", global().oldRootPool);
    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
    decBuffer = new RCDecBuffer(global().decPool);
    backupTrace = new BTTraceLocal(global().backupTrace);
    zero = new RCZero();
  }

  /**
   * Get the modified processor to use.
   */
  protected abstract TransitiveClosure getModifiedProcessor();

  /**
   * Get the root trace to use.
   */
  protected abstract TraceLocal getRootTrace();

  /****************************************************************************
   *
   * Collection
   */

  /**
   * {@inheritDoc}
   */
  @Override
  public void collect() {
    Phase.beginNewPhaseStack(Phase.scheduleComplex(global().collection));
  }

  @Override
  public void collectionPhase(short phaseId, boolean primary) {
    if (phaseId == RCBase.PREPARE) {
      getRootTrace().prepare();
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) backupTrace.prepare();
      return;
    }

    if (phaseId == RCBase.CLOSURE) {
      getRootTrace().completeTrace();
      newRootBuffer.flushLocal();
      return;
    }

    if (phaseId == RCBase.BT_CLOSURE) {
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) {
        backupTrace.completeTrace();
      }
      return;
    }

    if (phaseId == RCBase.PROCESS_OLDROOTBUFFER) {
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) return;
      ObjectReference current;
      while(!(current = oldRootBuffer.pop()).isNull()) {
        decBuffer.push(current);
      }
      return;
    }

    if (phaseId == RCBase.PROCESS_NEWROOTBUFFER) {
      ObjectReference current;
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) {
        while(!(current = newRootBuffer.pop()).isNull()) {
          if (RCHeader.testAndMark(current)) {
            if (RCHeader.initRC(current) == RCHeader.INC_NEW) {
              modBuffer.push(current);
            }
            backupTrace.processNode(current);
          } else {
            if (RCHeader.incRC(current) == RCHeader.INC_NEW) {
              modBuffer.push(current);
            }
          }
        }
        modBuffer.flushLocal();
        return;
      }
      while(!(current = newRootBuffer.pop()).isNull()) {
        if (RCHeader.incRC(current) == RCHeader.INC_NEW) {
          modBuffer.push(current);
        }
        oldRootBuffer.push(current);
      }
      oldRootBuffer.flushLocal();
      modBuffer.flushLocal();
      return;
    }

    if (phaseId == RCBase.PROCESS_MODBUFFER) {
      ObjectReference current;
      while(!(current = modBuffer.pop()).isNull()) {
        RCHeader.makeUnlogged(current);
        if (Space.isInSpace(RCBase.REF_COUNT, current)) {
          ExplicitFreeListSpace.testAndSetLiveBit(current);
        }
        VM.scanning.scanObject(getModifiedProcessor(), current);
      }
      return;
    }

    if (phaseId == RCBase.PROCESS_DECBUFFER) {
      ObjectReference current;
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) {
        while(!(current = decBuffer.pop()).isNull()) {
          if (RCHeader.isNew(current)) {
            if (Space.isInSpace(RCBase.REF_COUNT, current)) {
              RCBase.rcSpace.free(current);
            } else if (Space.isInSpace(RCBase.REF_COUNT_LOS, current)) {
              RCBase.rcloSpace.free(current);
            } else if (Space.isInSpace(RCBase.IMMORTAL, current)) {
              VM.scanning.scanObject(zero, current);
            }
          }
        }
        return;
      }
      while(!(current = decBuffer.pop()).isNull()) {
        if (RCHeader.isNew(current)) {
          if (Space.isInSpace(RCBase.REF_COUNT, current)) {
            RCBase.rcSpace.free(current);
          } else if (Space.isInSpace(RCBase.REF_COUNT_LOS, current)) {
            RCBase.rcloSpace.free(current);
          } else if (Space.isInSpace(RCBase.IMMORTAL, current)) {
            VM.scanning.scanObject(zero, current);
          }
        } else {
          if (RCHeader.decRC(current) == RCHeader.DEC_KILL) {
            decBuffer.processChildren(current);
            if (Space.isInSpace(RCBase.REF_COUNT, current)) {
              RCBase.rcSpace.free(current);
            } else if (Space.isInSpace(RCBase.REF_COUNT_LOS, current)) {
              RCBase.rcloSpace.free(current);
            } else if (Space.isInSpace(RCBase.IMMORTAL, current)) {
              VM.scanning.scanObject(zero, current);
            }
          }
        }
      }
      return;
    }

    if (phaseId == RCBase.RELEASE) {
      if (RCBase.CC_BACKUP_TRACE && RCBase.performCycleCollection) {
        backupTrace.release();
        global().oldRootPool.clearDeque(1);
      }
      getRootTrace().release();
      if (VM.VERIFY_ASSERTIONS) {
        VM.assertions._assert(newRootBuffer.isEmpty());
        VM.assertions._assert(modBuffer.isEmpty());
        VM.assertions._assert(decBuffer.isEmpty());
      }
      return;
    }

    super.collectionPhase(phaseId, primary);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  @Inline
  protected static RCBase global() {
    return (RCBase) VM.activePlan.global();
  }

  @Override
  public final TraceLocal getCurrentTrace() {
    return getRootTrace();
  }

  /** @return The current modBuffer instance. */
  @Inline
  public final ObjectReferenceDeque getModBuffer() {
    return modBuffer;
  }
}
