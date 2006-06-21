/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.semispace.SSMutator;
import org.mmtk.plan.semispace.*;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.vm.Barriers;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements <i>per-mutator thread</i> behavior and state for the
 * <i>GCTrace</i> plan, which implements a GC tracing algorithm.<p>
 * 
 * Specifically, this class defines <i>SS</i> mutator-time allocation, write
 * barriers, and per-mutator collection semantics.<p>
 * 
 * @see GCTrace for an overview of the GC trace algorithm.<p>
 * 
 * @see SSMutator
 * @see GCTrace
 * @see GCTraceCollector
 * @see StopTheWorldMutator
 * @see MutatorContext
 * @see SimplePhase#delegatePhase
 * 
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * 
 * @version $Revision$
 * @date $Date$
 */
public class GCTraceMutator extends SSMutator implements Uninterruptible {

	/****************************************************************************
   * 
   * Mutator-time allocation
   */

  /**
	 * Perform post-allocation actions.  For many allocators none are
	 * required.
   * 
	 * @param object The newly allocated object
	 * @param typeRef the type reference for the instance being created
	 * @param bytes The size of the space to be allocated (in bytes)
	 * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(ObjectReference object, ObjectReference typeRef,
			int bytes, int allocator)
	throws InlinePragma {
    /* Make the trace generator aware of the new object. */
    TraceGenerator.addTraceObject(object, allocator);

    super.postAlloc(object, typeRef, bytes, allocator);

    /* Now have the trace process aware of the new allocation. */
    GCTrace.traceInducedGC = TraceGenerator.MERLIN_ANALYSIS;
		TraceGenerator.traceAlloc(allocator == GCTrace.ALLOC_IMMORTAL, object, typeRef, bytes);
    GCTrace.traceInducedGC = false;
  }

	
	/****************************************************************************
   * 
   * Write barrier.
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
	 * @param metaDataA an int that encodes the source location
	 * @param metaDataB an int that encodes the source location
	 * being modified
	 * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  public final void writeBarrier(ObjectReference src, Address slot,
			ObjectReference tgt, Offset metaDataA, 
			int metaDataB, int mode) throws InlinePragma {
		TraceGenerator.processPointerUpdate(mode == PUTFIELD_WRITE_BARRIER,
				src, slot, tgt);
    Barriers.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
	 * A number of references are about to be copied from object
	 * <code>src</code> to object <code>dst</code> (as in an array
	 * copy).  Thus, <code>dst</code> is the mutated object.  Take
	 * appropriate write barrier actions.<p>
	 *
	 * @param src The source of the values to be copied
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
  public boolean writeBarrier(ObjectReference src, Offset srcOffset,
      ObjectReference dst, Offset dstOffset, int bytes) {
		/* These names seem backwards, but are defined to be compatable with the
		 * previous writeBarrier method. */
    Address slot = dst.toAddress().plus(dstOffset);
    Address tgtLoc = src.toAddress().plus(srcOffset);
    for (int i = 0; i < bytes; i += BYTES_IN_ADDRESS) {
      ObjectReference tgt = tgtLoc.loadObjectReference();
      TraceGenerator.processPointerUpdate(false, dst, slot, tgt);
      slot = slot.plus(BYTES_IN_ADDRESS);
      tgtLoc = tgtLoc.plus(BYTES_IN_ADDRESS);
    }
    return false;
  }

	/****************************************************************************
   * 
   * Collection
   */

  /**
   * Perform a per-mutator collection phase.
   * 
	 * @param phaseId The collection phase to perform
	 * @param primary perform any single-threaded local activities.
   */
  public void collectionPhase(int phaseId, boolean primary) {
    if (phaseId == SS.PREPARE_MUTATOR) {
      if (!GCTrace.traceInducedGC) {
        // rebind the semispace bump pointer to the appropriate semispace.
        ss.rebind(SS.toSpace());
      }
      super.collectionPhase(phaseId, primary);
      return;
    }

    super.collectionPhase(phaseId, primary);
  }
}
