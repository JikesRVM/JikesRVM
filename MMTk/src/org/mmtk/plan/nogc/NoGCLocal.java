/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2005
 */
package org.mmtk.plan.nogc;

import org.mmtk.plan.*;
import org.mmtk.policy.ImmortalLocal;
import org.mmtk.policy.Space;

import org.mmtk.utility.alloc.Allocator;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Assert;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * This class implements the thread-local state of the NoGC plan.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Daniel Frampton
 * @author Robin Garner
 * @version $Revision$
 * @date $Date$
 */
public class NoGCLocal extends PlanLocal implements Uninterruptible {

  /**
   * @return The active global plan as a <code>NoGC</code> instance.
   */
  private static final NoGC global() throws InlinePragma {
    return (NoGC)ActivePlan.global();
  }


  /************************************************************************
   * Instance fields
   */
  private final ImmortalLocal def;
  private final NoGCTraceLocal trace;

  /**
   * @return The current trace instance.
   */
  public final TraceLocal getCurrentTrace() { return trace; }

  /**
   * Constructor.  One instance is created per physical processor.
   */
  public NoGCLocal() {
    def = new ImmortalLocal(NoGC.defSpace);
    trace = new NoGCTraceLocal(global().trace);
  }

  /****************************************************************************
  *
  * Allocation
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
   * Perform a garbage collection
   */
  public final void collect() {
    Assert.fail("GC Triggered in NoGC Plan. Is -X:gc:ignoreSystemGC=true ?");
  }

  /**
   * Perform a (local) collection phase.
   *
   * @param phaseId The collection phase to perform
   * @param participating Is this thread participating in collection
   *        (as opposed to blocked in a JNI call)
   * @param primary perform any single-threaded local activities.
   */
  public final void collectionPhase(int phaseId, boolean participating,
                                    boolean primary) {
    Assert.fail("GC Triggered in NoGC Plan.");
//    if (phaseId == NoGC.PREPARE) {
//    }
//
//    if (phaseId == NoGC.BEGIN_CLOSURE) {
//      trace.startTrace();
//      return;
//    }
//
//    if (phaseId == NoGC.COMPLETE_CLOSURE) {
//      trace.completeTrace();
//      return;
//    }
//
//    if (phaseId == NoGC.RELEASE) {
//    }
//  super.collectionPhase(phaseId, participating, primary);
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

}
