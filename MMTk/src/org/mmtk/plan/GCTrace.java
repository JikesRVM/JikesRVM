/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 *
 * (C) Copyright Department of Computer Science,
 * University of Massachusetts, Amherst. 2003
 */
package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RawPageSpace;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.Options;
import org.mmtk.utility.deque.SortTODSharedDeque;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.TraceGenerator;
import org.mmtk.vm.Assert;
import org.mmtk.vm.Barriers;
import org.mmtk.vm.Memory;
import org.mmtk.vm.ObjectModel;
import org.mmtk.vm.Collection;

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
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 *
 * @version $Revision$
 * @date $Date$
 */
public class GCTrace extends SemiSpaceBase implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final boolean GENERATE_GC_TRACE = true;

  /* GC state */
  private static boolean traceInducedGC = false; // True if trace triggered GC
  private static boolean deathScan = false;
  private static boolean finalDead = false;

  /* Spaces */
  protected static RawPageSpace traceSpace = new RawPageSpace("trace", DEFAULT_POLL_FREQUENCY, META_DATA_MB);
  protected static final int TRACE = traceSpace.getID();

  /****************************************************************************
   *
   * Instance variables
   */

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    SortTODSharedDeque workList = new SortTODSharedDeque(traceSpace, 1);
    SortTODSharedDeque traceBuf = new SortTODSharedDeque(traceSpace, 1); 
    TraceGenerator.init(workList, traceBuf);
  }

  /**
   * Constructor
   */
  public GCTrace() {}

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot() throws InterruptiblePragma { 
    StopTheWorldGC.boot();
  }

  /**
   * The planExit method is called at RVM termination to allow the
   * trace process to finish.
   */
   public final void planExit(int value) {
    finalDead = true;
    traceInducedGC = false;
    deathScan = true;
  }

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
  public final void postAlloc(Address object, Address typeRef, int bytes,
			      int allocator)
    throws InlinePragma {
    /* Make the trace generator aware of the new object. */
    TraceGenerator.addTraceObject(object, allocator);
    switch (allocator) {
    case  ALLOC_DEFAULT: break;
    case ALLOC_IMMORTAL: immortalSpace.postAlloc(object); break;
    case      ALLOC_LOS: loSpace.initializeHeader(object); break;
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
    }
    /* Now have the trace process aware of the new allocation. */
    traceInducedGC = TraceGenerator.MERLIN_ANALYSIS;
    TraceGenerator.traceAlloc(allocator == ALLOC_IMMORTAL, object, typeRef, bytes);
    traceInducedGC = false;
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @return The address of the first byte of the allocated region
   */
  public final Address allocCopy(Address original, int bytes, 
				    int align, int offset) 
    throws InlinePragma {
    if (Assert.VERIFY_ASSERTIONS) Assert._assert(bytes <= LOS_SIZE_THRESHOLD);
    Address result = ss.alloc(bytes, align, offset);
    return result;
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param object The newly allocated object
   * @param typeRef the type reference for the instance being created
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(Address object, Address typeRef, int bytes) 
    throws InlinePragma {
    CopySpace.clearGCBits(object);
  } // do nothing

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @see org.mmtk.policy.Space#acquire(int)
   * @param mustCollect if <code>true</code> then a collection is
   * required and must be triggered.  Otherwise a collection is only
   * triggered if we deem it necessary.
   * @param space the space that triggered the polling (i.e. the space
   * into which an allocation is about to occur).
   * @return True if a collection has been triggered
   */
  public boolean poll(boolean mustCollect, Space space) 
    throws LogicallyUninterruptiblePragma {
    if (collectionsInitiated > 0 || !initialized || space == metaDataSpace)
      return false;
    mustCollect |= stressTestGCRequired();
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = space.reservedPages() - space.committedPages();
      if (space == copySpace0 || space == copySpace1)
	required = required<<1; // must account for copy reserve
      traceInducedGC = false;
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   *
   * Important notes:
   *   . Global actions are executed by only one thread
   *   . Thread-local actions are executed by all threads
   *   . The following order is guaranteed by BasePlan, with each
   *     separated by a synchronization barrier.:
   *      1. globalPrepare()
   *      2. threadLocalPrepare()
   *      3. threadLocalRelease()
   *      4. globalRelease()
   */

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    if (!traceInducedGC) super.globalPrepare();
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting the semi-space and large object
   * space allocators.
   */
  protected final void threadLocalPrepare(int count) {
    if (!traceInducedGC) super.threadLocalPrepare(count);
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the treadmill collector used by the
   * LOS).
   */
  protected final void threadLocalRelease(int count) {
    if (!traceInducedGC) super.threadLocalRelease(count);
  }

  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>StopTheWorld</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces and checking
   * whether the GC made progress.
   */
  protected final void globalRelease() {
    if (!traceInducedGC) {
      /* Perform the death time calculations */
      deathScan = true;
      TraceGenerator.postCollection();
      deathScan = false;
      /* release each of the collected regions */
      super.globalRelease();
    } else {
      /* Clean up following a trace induced scan */
      progress = true;
      deathScan = false;
    }
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies (such as those needed for trace generation)
   * and taking the appropriate actions.
   *
   * @param object The object reference to be traced.  In certain
   * cases, this should <i>NOT</i> be an interior pointer.
   * @return The possibly moved reference.
   */
  public static final Address traceObject(Address object) throws InlinePragma {
    if (object.isZero()) return object;
    if (traceInducedGC) {
      TraceGenerator.rootEnumerate(object);
      return object;
    } else if (deathScan) {
      TraceGenerator.propagateDeathTime(object);
      return object;
    } else
      return SemiSpaceBase.traceObject(object);
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param object The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final Address traceObject(Address object, boolean root) {
    return traceObject(object);  // root or non-root is of no consequence here
  }

  /**
   * Forward the object referred to by a given address and update the
   * address if necessary.  This <i>does not</i> enqueue the referent
   * for processing; the referent must be explicitly enqueued if it is
   * to be processed.
   *
   * @param location The location whose referent is to be forwarded if
   * necessary.  The location will be updated if the referent is
   * forwarded.
   */
  public static void forwardObjectLocation(Address location) 
    throws InlinePragma {
    if (traceInducedGC) {
      Address object = location.loadAddress();
      if (!object.isZero())
        TraceGenerator.rootEnumerate(object);
    } else
      SemiSpaceBase.forwardObjectLocation(location);
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(Address object) {
    if (object.isZero()) return false;
    if (traceInducedGC)
      return true;
    else
      return SemiSpaceBase.isLive(object);
  }

 /**
   * Return true if <code>obj</code> is a reachable object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a reachable object;
   * unreachable objects may still be live, however
   */
  public final boolean isReachable(Address object) {
    if (finalDead) return false;
    if (object.isZero()) return false;
    Space space = Space.getSpaceForObject(object);
    if (space == copySpace0)
      return ((hi) ? copySpace0.isLive(object) : true);
    else if (space == copySpace1)
      return ((!hi) ? copySpace1.isLive(object) : true);
    else if (space == loSpace)
      return loSpace.isLive(object);
    else
      return super.isReachable(object);
  }

  // XXX Missing Javadoc comment.
  public static boolean willNotMove(Address object) {
    if (traceInducedGC)
      return true;
    else
      return SemiSpaceBase.willNotMove(object);
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
  public final void writeBarrier(Address src, Address slot,
                                 Address tgt, int metaDataA, 
				 int metaDataB, int mode) 
    throws InlinePragma {
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
  public boolean writeBarrier(Address src, int srcOffset,
			      Address dst, int dstOffset,
			      int bytes) {
    /* These names seem backwards, but are defined to be compatable with the
     * previous writeBarrier method. */
    Address slot = dst.add(dstOffset);
    Address tgtLoc = src.add(srcOffset);
    for (int i = 0; i < bytes; i += BYTES_IN_ADDRESS) {
      Address tgt = tgtLoc.loadAddress();
      TraceGenerator.processPointerUpdate(false, dst, slot, tgt);
      slot = slot.add(BYTES_IN_ADDRESS);
      tgtLoc = tgtLoc.add(BYTES_IN_ADDRESS);
    }
    return false;
  }
  
  /****************************************************************************
   *
   * Space management
   */

  /**
   * @return Since trace induced collections are not called to free up memory,
   * their failure to return memory isn't cause for concern.
   */
  public static boolean isLastGCFull () {
    return !traceInducedGC;
  }

  /****************************************************************************
   *
   * Miscellaneous
   */
}
