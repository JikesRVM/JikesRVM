/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan.refcount.generational;

import org.mmtk.plan.refcount.RCBaseCollector;
import org.mmtk.plan.refcount.fullheap.RC;
import org.mmtk.plan.TraceLocal;
import org.mmtk.policy.Space;
import org.mmtk.policy.CopySpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.Constants;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Memory;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-collector thread</i> behavior and
 * state for the <i>GenRC</i> plan, a generational reference
 * counting collector.<p>
 * 
 * Specifically, this class defines collection-time allocation (promotion
 * from the nursery) and basic per-collector collection semantics.<p>
 *
 * @see GenRC for a description of the generational reference counting
 * algorithm.<p>
 * 
 * FIXME Currently GenRC does not properly separate mutator and collector
 * behaviors, so most of the collection logic in GenRCMutator should really
 * be per-collector thread, not per-mutator thread.
 * 
 * @see RCBaseCollector
 * @see GenRC
 * @see GenRCMutator
 * @see StopTheWorldCollector
 * @see CollectorContext
 * @see SimplePhase#delegatePhase
 *
 * $Id$
 *
 * @author Steve Blackburn
 * @author Robin Garner
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GenRCCollector extends RCBaseCollector
implements Uninterruptible, Constants {
	
  //	FIXME This hack is a consequence of zero collector/mutator separation in RC...
	private final GenRCMutator fixme() {
		return (GenRCMutator)ActivePlan.mutator();
	}
	
	/****************************************************************************
	 * Instance variables
	 */	
	public GenRCTraceLocal trace = new GenRCTraceLocal(global().trace);
	
	/****************************************************************************
	 *
	 * Initialization
	 */
	
	/**
	 * Constructor
	 */
	public GenRCCollector() {
		global().remsetPool.newConsumer();
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
		if (Assert.VERIFY_ASSERTIONS) Assert._assert(allocator == GenRC.ALLOC_RC);
		return fixme().rc.alloc(bytes, align, offset, false);  // FIXME is this right???
	}
	
	/**
	 * Perform any post-copy actions.  In this case nothing is required.
	 *
	 * @param object The newly allocated object
	 * @param typeRef the type reference for the instance being created
	 * @param bytes The size of the space to be allocated (in bytes)
	 */
	public final void postCopy(ObjectReference object, ObjectReference typeRef,
			int bytes, int allocator) throws InlinePragma {
		CopySpace.clearGCBits(object);
		RefCountSpace.initializeHeader(object, typeRef, false);
		RefCountSpace.makeUnlogged(object);
		RefCountLocal.unsyncLiveObject(object);
		if (RefCountSpace.RC_SANITY_CHECK) {
			RefCountLocal.sanityAllocCount(object);
		}
	}
	
	/****************************************************************************
	 *
	 * Collection
	 */
	
	/**
	 * Perform a per-collector collection phase.
	 *
	 * @param phaseId The collection phase to perform
	 * @param participating Is this thread participating in collection
	 *        (as opposed to blocked in a JNI call)
	 * @param primary Perform any single-threaded activities using this thread.
	 */
	public void collectionPhase(int phaseId, boolean participating,
			boolean primary) {
		if (phaseId == RC.PREPARE) {
			Memory.collectorPrepareVMSpace();
			return;
		}
		
		if (phaseId == RC.START_CLOSURE) {
			trace.startTrace();
			return;
		}
		
		if (phaseId == RC.COMPLETE_CLOSURE) {
			trace.completeTrace();
			return;
		}
		
		if (phaseId == RC.RELEASE) {
			Memory.collectorReleaseVMSpace();
			if (Options.verbose.getValue() > 2) fixme().rc.printStats();
			return;
		}
		super.collectionPhase(phaseId, participating, primary);
	}
	
	/**
	 * Trace a reference during an increment sanity traversal.  This is
	 * only used as part of the ref count sanity check, and it forms the
	 * basis for a transitive closure that assigns a reference count to
	 * each object.
	 *
	 * @param object The object being traced
	 * @param location The location from which this object was
	 * reachable, null if not applicable.
	 * @param root <code>true</code> if the object is being traced
	 * directly from a root.
	 */
	public final void incSanityTrace(ObjectReference object, Address location,
			boolean root) {
		if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
		// if nursery, then get forwarded RC object
		if (Space.isInSpace(GenRC.NS, object)) {
			if (Assert.VERIFY_ASSERTIONS)
				Assert._assert(CopySpace.isForwarded(object));
			object = CopySpace.getForwardingPointer(object);
		}
		
		if (GenRC.isRCObject(object)) {
			if (RefCountSpace.incSanityRC(object, root))
				Scan.enumeratePointers(object, fixme().sanityEnum);
		} else if (RefCountSpace.markSanityRC(object))
			Scan.enumeratePointers(object, fixme().sanityEnum);
	}
	
	/**
	 * Trace a reference during a check sanity traversal.  This is only
	 * used as part of the ref count sanity check, and it forms the
	 * basis for a transitive closure that checks reference counts
	 * against sanity reference counts.  If the counts are not matched,
	 * an error is raised.
	 *
	 * @param object The object being traced
	 * @param location The location from which this object was
	 * reachable, null if not applicable.
	 */
	public final void checkSanityTrace(ObjectReference object,
			Address location) {
		if (Assert.VERIFY_ASSERTIONS) Assert._assert(!object.isNull());
		// if nursery, then get forwarded RC object
		if (Space.isInSpace(GenRC.NS, object)) {
			if (Assert.VERIFY_ASSERTIONS)
				Assert._assert(CopySpace.isForwarded(object));
			object = CopySpace.getForwardingPointer(object);
		}
		
		if (GenRC.isRCObject(object)) {
			if (RefCountSpace.checkAndClearSanityRC(object)) {
				Scan.enumeratePointers(object, fixme().sanityEnum);
				fixme().rc.addLiveSanityObject(object);
			}
		} else if (RefCountSpace.unmarkSanityRC(object)) {
			Scan.enumeratePointers(object, fixme().sanityEnum);
		}
	}
	
	
	/****************************************************************************
	 *
	 * Miscellaneous
	 */
	
	/** @return The active global plan as a <code>GenRC</code> instance. */
	private static final GenRC global() throws InlinePragma {
		return (GenRC)ActivePlan.global();
	}
	
	public final TraceLocal getCurrentTrace() {
		return trace;
	}
}

