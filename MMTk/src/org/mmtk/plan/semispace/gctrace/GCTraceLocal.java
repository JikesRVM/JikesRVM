/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan.semispace.gctrace;

import org.mmtk.plan.TraceLocal;
import org.mmtk.plan.semispace.*;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.vm.ActivePlan;
import org.mmtk.vm.Barriers;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This plan has been modified slightly to perform the processing necessary
 * for GC trace generation.  To maximize performance, it attempts to remain
 * as faithful as possible to semiSpace/Plan.java.
 *
 * The generated trace format is as follows:
 *    B 345678 12
 *      (Object 345678 was created in the boot image with a size of 12 bytes)
 *    U 59843 234 47298
 *      (Update object 59843 at the slot at offset 234 to refer to 47298)
 *    S 1233 12345
 *      (Update static slot 1233 to refer to 12345)
 *    T 4567 78924
 *      (The TIB of 4567 is set to refer to 78924)
 *    D 342789
 *      (Object 342789 became unreachable)
 *    A 6860 24 346648 3
 *      (Object 6860 was allocated, requiring 24 bytes, with fp 346648 on
 *        thread 3; this allocation has perfect knowledge)
 *    a 6884 24 346640 5
 *      (Object 6864 was allocated, requiring 24 bytes, with fp 346640 on
 *        thread 5; this allocation DOES NOT have perfect knowledge)
 *    I 6860 24 346648 3
 *      (Object 6860 was allocated into immortal space, requiring 24 bytes,
 *        with fp 346648 on thread 3; this allocation has perfect knowledge)
 *    i 6884 24 346640 5
 *      (Object 6864 was allocated into immortal space, requiring 24 bytes,
 *        with fp 346640 on thread 5; this allocation DOES NOT have perfect
 *        knowledge)
 *    48954->[345]LObject;:blah()V:23   Ljava/lang/Foo;
 *      (Citation for: a) where the was allocated, fp of 48954,
 *         at the method with ID 345 -- or void Object.blah() -- and bytecode
 *         with offset 23; b) the object allocated is of type java.lang.Foo)
 *    D 342789 361460
 *      (Object 342789 became unreachable after 361460 was allocated)
 *
 * This class implements a simple semi-space collector. See the Jones
 * & Lins GC book, section 2.2 for an overview of the basic
 * algorithm. This implementation also includes a large object space
 * (LOS), and an uncollected "immortal" space.<p>
 *
 * All plans make a clear distinction between <i>global</i> and
 * <i>thread-local</i> activities.  Global activities must be
 * synchronized, whereas no synchronization is required for
 * thread-local activities.  Instances of Plan map 1:1 to "kernel
 * threads" (aka CPUs or in Jikes RVM, VM_Processors).  Thus instance
 * methods allow fast, unsychronized access to Plan utilities such as
 * allocation and collection.  Each instance rests on static resources
 * (such as memory and virtual memory resources) which are "global"
 * and therefore "static" members of Plan.  This mapping of threads to
 * instances is crucial to understanding the correctness and
 * performance proprties of this plan.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 * @author Daniel Frampton
 * @author Robin Garner
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class GCTraceLocal extends SSLocal implements Uninterruptible {

  GCTraceTraceLocal inducedTrace = new GCTraceTraceLocal(global().ssTrace);
  
  /**
   * @return The active global plan as a <code>GCTrace</code> instance.
   */
  private static final GCTrace global() throws InlinePragma {
    return (GCTrace)ActivePlan.global();
  }
  
  /**
   * Constructor
   */
  public GCTraceLocal() {}

  /****************************************************************************
   *
   * Allocation
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
   * Collection
   */
  
  public void collectionPhase(int phaseId, boolean participating, boolean primary) {
    if(GCTrace.traceInducedGC) {
      if (phaseId == GCTrace.PREPARE || 
          phaseId == GCTrace.RELEASE) {
        // Do nothing.
        return;
      }
      
      if (phaseId == GCTrace.START_CLOSURE) {
        inducedTrace.startTrace();
        return;
      }
      
      if (phaseId == GCTrace.COMPLETE_CLOSURE) {
        inducedTrace.completeTrace();
        return;
      }
    }
    
    // Delegate up.
    super.collectionPhase(phaseId, participating, primary);
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
   * @param locationMetadata an int that encodes the source location
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
    Address slot = dst.toAddress().add(dstOffset);
    Address tgtLoc = src.toAddress().add(srcOffset);
    for (int i = 0; i < bytes; i += BYTES_IN_ADDRESS) {
      ObjectReference tgt = tgtLoc.loadObjectReference();
      TraceGenerator.processPointerUpdate(false, dst, slot, tgt);
      slot = slot.add(BYTES_IN_ADDRESS);
      tgtLoc = tgtLoc.add(BYTES_IN_ADDRESS);
    }
    return false;
  }
  
  /**
   * @return The current trace instance
   */
  public TraceLocal getCurrentTrace() {
    return GCTrace.traceInducedGC ? inducedTrace : trace;
  }
}
