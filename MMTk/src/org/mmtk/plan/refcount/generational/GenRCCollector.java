/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.TraceStep;
import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.plan.refcount.RCHeader;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ExplicitFreeListLocal;
import org.mmtk.utility.Constants;
import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-collector thread</i> behavior 
 * and state for the <i>GenRC</i> plan, which implements a generational
 * reference counting collector.<p>
 * 
 * Specifically, this class defines <i>RC</i> collection behavior
 * (through <code>trace</code> and the <code>collectionPhase</code>
 * method).<p>
 * 
 * @see GenRC for an overview of the reference counting algorithm.<p>
 * 
 * FIXME The SegregatedFreeList class (and its decendents such as
 * MarkSweepLocal) does not properly separate mutator and collector
 * behaviors, so the ms field below should really not exist in
 * this class as there is no collection-time allocation in this
 * collector.
 * 
 * @see GenRC
 * @see GenRCMutator
 * @see org.mmtk.plan.StopTheWorldCollector
 * @see org.mmtk.plan.CollectorContext
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
public abstract class GenRCCollector extends RCBaseCollector
implements Uninterruptible, Constants {

  /****************************************************************************
   * Instance fields
   */
  public final ExplicitFreeListLocal rc;  
  public final GenRCTraceLocal trace;
  public final GenRCModifiedProcessor modProcessor;

  /****************************************************************************
   * Initialization
   */

  /**
   * Constructor
   */
  public GenRCCollector() {
    trace = new GenRCTraceLocal(global().rcTrace);
    rc = new ExplicitFreeListLocal(GenRC.rcSpace);
    modProcessor = new GenRCModifiedProcessor(trace);
  }
  
  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-collector collection phase.
   * 
   * @param phaseId The collection phase to perform
   * @param primary Perform any single-threaded activities using this thread.
   */
  public void collectionPhase(int phaseId, boolean primary)
      throws InlinePragma {
    if (phaseId == GenRC.PREPARE) {
      super.collectionPhase(phaseId, primary);
      rc.prepare();
      return;
    }

    if (phaseId == GenRC.RELEASE) {
      super.collectionPhase(phaseId, primary);
      rc.releaseCollector();
      rc.releaseMutator();
      return;
    }

    super.collectionPhase(phaseId, primary);
  }
  
  /****************************************************************************
  *
  * Collection-time allocation
  */
  
  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(ObjectReference original, int bytes,
                                 int align, int offset, int allocator)
    throws InlinePragma {
    if (VM.VERIFY_ASSERTIONS) {
      VM.assertions._assert(allocator == GenRC.ALLOC_RC);
    }
    return rc.alloc(bytes, align, offset, true);
  }
  
  /**
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(ObjectReference object, ObjectReference typeRef,
                             int bytes, int allocator)
    throws InlinePragma {
    CopySpace.clearGCBits(object);
    RCHeader.initializeHeader(object, typeRef, false);
    RCHeader.makeUnlogged(object);
    ExplicitFreeListLocal.unsyncLiveObject(object);
  }
  
  /****************************************************************************
   * 
   * Miscellaneous
   */

  /** @return The active global plan as an <code>MS</code> instance. */
  private static final GenRC global() throws InlinePragma {
    return (GenRC) VM.activePlan.global();
  }
  
  /** @return The current trace instance. */
  public final TraceLocal getCurrentTrace() {
    return trace;
  }
  
  /** @return The current modified object processor. */
  public final TraceStep getModifiedProcessor() {
    return modProcessor;
  }
}
