/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2006
 */
package org.mmtk.plan.generational;

import org.mmtk.plan.*;
import org.mmtk.policy.CopyLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.statistics.Stats;

import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Barriers;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This abstract class implements <i>per-mutator thread</i> behavior 
 * and state for <i>generational copying collectors</i>.<p>
 * 
 * Specifically, this class defines mutator-time allocation into the nursery;
 * write barrier semantics, and per-mutator thread collection semantics
 * (flushing and restoring per-mutator allocator and remset state).
 * 
 * @see Gen
 * @see GenCollector
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
public class GenMutator extends StopTheWorldMutator implements Uninterruptible {
	
	/*****************************************************************************
	 *
	 * Instance fields
	 */
	protected final CopyLocal nursery = new CopyLocal(Gen.nurserySpace);
	
	protected final WriteBuffer remset;
	protected final AddressPairDeque arrayRemset;
	
	/****************************************************************************
	 *
	 * Initialization
	 */
	
	/**
	 * Constructor
	 * 
	 * Note that each mutator is a producer of remsets, while each
	 * collector is a consumer.  The <code>GenCollector</code> class
	 * is responsible for construction of the consumer. 
	 * @see GenCollector 
	 */
	public GenMutator() {
		remset = new WriteBuffer(global().remsetPool);
		arrayRemset = new AddressPairDeque(global().arrayRemsetPool);
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
	 * @return The low address of the allocated memory.
	 */
	public Address alloc(int bytes, int align, int offset, int allocator)
	throws InlinePragma {
		if (allocator == Gen.ALLOC_NURSERY) {
			if (Stats.GATHER_MARK_CONS_STATS) Gen.nurseryCons.inc(bytes);
			return nursery.alloc(bytes, align, offset);
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
		if (allocator != Gen.ALLOC_NURSERY) {
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
		if (a == nursery) return Gen.nurserySpace;
		
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
		if (space == Gen.nurserySpace) return nursery;
		return super.getAllocatorFromSpace(space);
	}
	
	/****************************************************************************
	 *
	 * Barriers
	 */
	
	/**
	 * A new reference is about to be created.  Take appropriate write
	 * barrier actions.<p>
	 *
	 * In this case, we remember the address of the source of the
	 * pointer if the new reference points into the nursery from
	 * non-nursery space.
	 *
	 * @param src The object into which the new reference will be stored
	 * @param slot The address into which the new reference will be
	 * stored.
	 * @param tgt The target of the new reference
	 * @param metaDataA A field used by the VM to create a correct store.
	 * @param metaDataB A field used by the VM to create a correct store.
	 * @param mode The mode of the store (eg putfield, putstatic etc)
	 */
	public final void writeBarrier(ObjectReference src, Address slot,
			ObjectReference tgt, Offset metaDataA,
			int metaDataB, int mode)
	throws InlinePragma {
		if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbFast.inc();
		if (slot.LT(Gen.NURSERY_START) && tgt.toAddress().GE(Gen.NURSERY_START)) {
			if (Gen.GATHER_WRITE_BARRIER_STATS) Gen.wbSlow.inc();
			remset.insert(slot);
		}
		Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
	}
	
	/**
	 * A number of references are about to be copied from object
	 * <code>src</code> to object <code>dst</code> (as in an array
	 * copy).  Thus, <code>dst</code> is the mutated object.  Take
	 * appropriate write barrier actions.<p>
	 *
	 * In this case, we remember the mutated source address range and
	 * will scan that address range at GC time.
	 *
	 * @param src The source of the values to copied
	 * @param srcOffset The offset of the first source address, in
	 * bytes, relative to <code>src</code> (in principle, this could be
	 * negative).
	 * @param dst The mutated object, i.e. the destination of the copy.
	 * @param dstOffset The offset of the first destination address, in
	 * bytes relative to <code>tgt</code> (in principle, this could be
	 * negative).
	 * @param bytes The size of the region being copied, in bytes.
	 * @return True if the update was performed by the barrier, false if
	 * left to the caller (always false in this case).
	 */
	public final boolean writeBarrier(ObjectReference src, Offset srcOffset,
			ObjectReference dst, Offset dstOffset,
			int bytes) throws InlinePragma {
		// We can ignore when src is in old space, right?
		if (dst.toAddress().LT(Gen.NURSERY_START))
			arrayRemset.insert(dst.toAddress().plus(dstOffset),
					dst.toAddress().plus(dstOffset.plus(bytes)));
		return false;
	}  
	
	/****************************************************************************
	 *
	 * Collection
	 */
	
	/**
	 * Perform a per-mutator collection phase.
	 */
	public void collectionPhase(int phaseId, boolean participating,
			boolean primary)
	throws NoInlinePragma {
		
		if (phaseId == Gen.PREPARE_MUTATOR) {
			nursery.rebind(Gen.nurserySpace);
			if (global().traceFullHeap()) {
				super.collectionPhase(phaseId, participating,primary);
				remset.resetLocal();
				arrayRemset.resetLocal();
			}
			remset.flushLocal();
			arrayRemset.flushLocal();
			return;
		}
		
		if (phaseId == Gen.RELEASE_MUTATOR) {
			if (global().traceFullHeap()) {
				super.collectionPhase(phaseId, participating, primary);
			}
			remset.flushLocal();
			arrayRemset.flushLocal();
			return;
		}
		
		super.collectionPhase(phaseId, participating, primary);
	}
	
	/****************************************************************************
	 *
	 * Miscellaneous
	 */
	
	/** @return The active global plan as a <code>Gen</code> instance. */
	private static final Gen global() throws InlinePragma {
		return (Gen)ActivePlan.global();
	}
}
