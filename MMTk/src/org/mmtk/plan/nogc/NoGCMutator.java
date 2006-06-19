/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.nogc;

import org.mmtk.plan.MutatorContext;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state
 * for the <i>NoGC</i> plan, which simply allocates (without ever collecting
 * until the available space is exhausted.<p>
 * 
 * Specifically, this class defines <i>NoGC</i> mutator-time allocation
 * through a bump pointer (<code>def</code>) and includes stubs for
 * per-mutator thread collection semantics (since there is no collection
 * in this plan, these remain just stubs).
 * 
 * @see NoGC
 * @see NoGCCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 *
 * $Id$
 *
 * @author Steve Blackburn
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class NoGCMutator extends MutatorContext implements Uninterruptible {
	
	/************************************************************************
	 * Instance fields
	 */
	private final ImmortalLocal def;  // the default allocator
	
	/************************************************************************
	 *
	 * Initialization
	 */
	
	/**
	 * Constructor.  One instance is created per physical processor.
	 */
	public NoGCMutator() {
		def = new ImmortalLocal(NoGC.defSpace);
	}
	
	/****************************************************************************
	 *
	 * Mutator-time allocation
	 */
	
	/**
	 * Allocate memory for an object.
	 *
	 * @param bytes The number of bytes required for the object.
	 * @param align Required alignment for the object.
	 * @param offset Offset associated with the alignment.
	 * @param allocator The allocator associated with this request.
	 * @return The address of the newly allocated memory.
	 */
	public Address alloc(int bytes, int align, int offset, int allocator)
	throws InlinePragma {
		if (allocator == NoGC.ALLOC_DEFAULT) {
			return def.alloc(bytes, align, offset);
		}
		return super.alloc(bytes, align, offset, allocator);
	}
	
	/**
	 * Perform post-allocation actions.  For many allocators none are
	 * required.
	 *
	 * @param ref The newly allocated object
	 * @param typeRef the type reference for the instance being created
	 * @param bytes The size of the space to be allocated (in bytes)
	 * @param allocator The allocator number to be used for this allocation
	 */
	public void postAlloc(ObjectReference ref, ObjectReference typeRef,
			int bytes, int allocator) throws InlinePragma {
		if (allocator != NoGC.ALLOC_DEFAULT) {
			super.postAlloc(ref, typeRef, bytes, allocator);
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
	 * <code>null</code> if there is no space associated with
	 * <code>a</code>.
	 */
	public Space getSpaceFromAllocator(Allocator a) {
		if (a == def) return NoGC.defSpace;
		
		// a does not belong to this plan instance
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
		if (space == NoGC.defSpace) return def;
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
	 * @param primary perform any single-threaded local activities.
	 */
	public final void collectionPhase(int phaseId, boolean participating,
			boolean primary) {
		Assert.fail("GC Triggered in NoGC Plan.");
		/*
		 if (phaseId == NoGC.PREPARE_MUTATOR) {
		 }
		 
		 if (phaseId == NoGC.RELEASE_MUTATOR) {
		 }
		 super.collectionPhase(phaseId, participating, primary);
		 */
	}
	
}
