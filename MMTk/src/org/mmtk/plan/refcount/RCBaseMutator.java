/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005, 2006
 */
package org.mmtk.plan.refcount;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.plan.refcount.cd.CDMutator;
import org.mmtk.plan.refcount.cd.NullCDMutator;
import org.mmtk.plan.refcount.cd.TrialDeletionMutator;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.policy.ExplicitLargeObjectLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.deque.ObjectReferenceDeque;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior 
 * and state for the <i>RCBase</i> plan, which implements the 
 * base functionality for reference counted collectors.
 * 
 * @see RCBase for an overview of the reference counting algorithm.<p>
 * 
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 * 
 * @see RCBase
 * @see RCBaseCollector
 * @see org.mmtk.plan.StopTheWorldMutator
 * @see org.mmtk.plan.MutatorContext
 * @see org.mmtk.plan.SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class RCBaseMutator extends StopTheWorldMutator implements Uninterruptible {

  /****************************************************************************
   * Instance fields
   */

  public ExplicitFreeListLocal rc;
  public ExplicitLargeObjectLocal los;
  
  public ObjectReferenceDeque modBuffer;
  public DecBuffer decBuffer;
  
  private NullCDMutator nullCD;
  private TrialDeletionMutator trialDeletionCD;

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Constructor
   */
  public RCBaseMutator() {
    rc = new ExplicitFreeListLocal(RCBase.rcSpace);
    los = new ExplicitLargeObjectLocal(RCBase.loSpace);
    decBuffer = new DecBuffer(global().decPool);
    modBuffer = new ObjectReferenceDeque("mod buf", global().modPool);
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      nullCD = new NullCDMutator();
      break;
    case RCBase.TRIAL_DELETION:
      trialDeletionCD = new TrialDeletionMutator();
      break;
    }
  }

  /****************************************************************************
   * 
   * Mutator-time allocation
   */

  /**
   * Allocate memory for an object. This class handles the default allocator
   * from the mark sweep space, and delegates everything else to the
   * superclass.
   * 
   * @param bytes The number of bytes required for the object.
   * @param align Required alignment for the object.
   * @param offset Offset associated with the alignment.
   * @param allocator The allocator associated with this request.
   * @param site Allocation site
   * @return The low address of the allocated memory.
   */
  public Address alloc(int bytes, int align, int offset, int allocator, int site)
      throws InlinePragma {
    switch(allocator) {
      case RCBase.ALLOC_RC:
        return rc.alloc(bytes, align, offset, false);
      case RCBase.ALLOC_LOS:
        return los.alloc(bytes, align, offset, false);
      default:
        return super.alloc(bytes, align, offset, allocator, site);
    }
  }

  /**
   * Perform post-allocation actions.  Initialize the object header for
   * objects in the mark-sweep space, and delegate to the superclass for
   * other objects.
   *
   * @param ref The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public void postAlloc(ObjectReference ref, ObjectReference typeRef,
      int bytes, int allocator) throws InlinePragma {
    switch(allocator) {
    case RCBase.ALLOC_RC:
      ExplicitFreeListLocal.unsyncLiveObject(ref);
    case RCBase.ALLOC_LOS:
    case RCBase.ALLOC_IMMORTAL:
      if (RCBase.WITH_COALESCING_RC) modBuffer.push(ref);
      RCHeader.initializeHeader(ref, typeRef, true);
      decBuffer.push(ref);
      break;
  default:
      if (RCBase.WITH_COALESCING_RC) modBuffer.push(ref);
      break;
    }
  }

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.
   * 
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   *         <code>null</code> if there is no space associated with
   *         <code>a</code>.
   */
  public Space getSpaceFromAllocator(Allocator a) {
    if (a == rc ) return RCBase.rcSpace;
    if (a == los) return RCBase.loSpace;
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.
   * 
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  public Allocator getAllocatorFromSpace(Space space) {
    if (space == RCBase.rcSpace) return rc;
    if (space == RCBase.loSpace) return los;
    return super.getAllocatorFromSpace(space);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  public void collectionPhase(int phaseId, boolean primary)
      throws InlinePragma {

    if (phaseId == RCBase.PREPARE_MUTATOR) {
      rc.prepare();
      los.prepare();
      decBuffer.flushLocal();
      modBuffer.flushLocal();
      return;
    }

    if (phaseId == RCBase.RELEASE_MUTATOR) {
      los.release();
      rc.releaseCollector();
      rc.releaseMutator(); // FIXME see block comment at top of this class
      return;
    }

    if (!cycleDetector().collectionPhase(phaseId)) {
      super.collectionPhase(phaseId, primary);
    }
  }

  /****************************************************************************
   * 
   * RC methods
   */

  /**
   * Add an object to the dec buffer for this mutator.
   * 
   * @param object The object to add
   */
  public final void addToDecBuffer(ObjectReference object) {
    decBuffer.push(object);
  }
  
  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as an <code>RC</code> instance. */
  private static final RCBase global() throws InlinePragma {
    return (RCBase) VM.activePlan.global();
  }
  
  /** @return The active cycle detector instance */
  public final CDMutator cycleDetector() throws InlinePragma {
    switch (RCBase.CYCLE_DETECTOR) {
    case RCBase.NO_CYCLE_DETECTOR:
      return nullCD;
    case RCBase.TRIAL_DELETION:
      return trialDeletionCD;
    }
    
    VM.assertions.fail("No cycle detector instance found.");
    return null;
  }
}
