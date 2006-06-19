/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.markcompact;

import org.mmtk.plan.StopTheWorldMutator;
import org.mmtk.policy.MarkCompactLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior 
 * and state for the <i>MC</i> plan, which implements a full-heap
 * mark-compact collector.<p>
 * 
 * Specifically, this class defines <i>MC</i> mutator-time allocation
 * and per-mutator thread collection semantics (flushing and restoring
 * per-mutator allocator state).
 * 
 * @see MC for an overview of the mark-compact algorithm.<p>
 *
 * FIXME Currently MC does not properly separate mutator and collector
 * behaviors, so some of the collection logic here should really be
 * per-collector thread, not per-mutator thread.
 * 
 * @see MC
 * @see MCCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 * 
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class MCMutator extends StopTheWorldMutator implements Uninterruptible {
	
	/****************************************************************************
	 * Instance fields
	 */
	private MarkCompactLocal mc;
	
	/****************************************************************************
	 *
	 * Initialization
	 */
	
	/**
	 * Constructor
	 */
	public MCMutator() {
		mc = new MarkCompactLocal(MC.mcSpace);
	}
	
	/****************************************************************************
	 *
	 * Mutator-time allocation
	 */
	
	/**
	 * Allocate memory for an object.  This class handles the default allocator
	 * from the mark sweep space, and delegates everything else to the
	 * superclass.
	 *
	 * @param bytes The number of bytes required for the object.
	 * @param align Required alignment for the object.
	 * @param offset Offset associated with the alignment.
	 * @param allocator The allocator associated with this request.
	 * @return The low address of the allocated memory.
	 */
	public Address alloc(int bytes, int align, int offset, int allocator)
	throws InlinePragma {
		if (allocator == MC.ALLOC_DEFAULT) {
			return mc.alloc(bytes, align, offset);
		}
		return super.alloc(bytes, align, offset, allocator);
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
		if (allocator == MC.ALLOC_DEFAULT)
			MC.mcSpace.initializeHeader(ref);
		else
			super.postAlloc(ref, typeRef, bytes, allocator);
	}
	
	/**
	 * Return the space into which an allocator is allocating.  This
	 * particular method will match against those spaces defined at this
	 * level of the class hierarchy.  Subclasses must deal with spaces
	 * they define and refer to superclasses appropriately.
	 *
	 * @param a An allocator
	 * @return The space into which <code>a</code> is allocating, or
	 * <code>null</code> if there is no space associated with
	 * <code>a</code>.
	 */
	public Space getSpaceFromAllocator(Allocator a) {
		if (a == mc) return MC.mcSpace;
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
		if (space == MC.mcSpace) return mc;
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
	 * @param participating Is this thread participating in collection
	 *        (as opposed to blocked in a JNI call)
	 * @param primary Perform any single-threaded activities using this thread.
	 */
	public final void collectionPhase(int phaseId, boolean participating,
			boolean primary)
	throws InlinePragma {
		if (phaseId == MC.PREPARE_MUTATOR) {
			super.collectionPhase(phaseId, participating, primary);
			return;
		}
		
		/* FIXME this needs to be made a per-collector phase */	
		if (phaseId == MC.CALCULATE_FP) {
			mc.calculateForwardingPointers();
			return;
		}
		
		/* FIXME this needs to be made a per-collector phase */	
		if (phaseId == MC.COMPACT) {
			mc.compact();
			return;
		}
		
		if (phaseId == MC.RELEASE_MUTATOR) {
			super.collectionPhase(phaseId, participating, primary);
			return;
		}
		super.collectionPhase(phaseId, participating, primary);
	}
	
}
