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
import org.mmtk.policy.TreadmillSpace;
import org.mmtk.policy.TreadmillLocal;
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
public class GCTrace extends StopTheWorldGC implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean MOVES_OBJECTS = true;
  public static final int GC_HEADER_BITS_REQUIRED = CopySpace.LOCAL_GC_BITS_REQUIRED;
  public static final int GC_HEADER_BYTES_REQUIRED = CopySpace.GC_HEADER_BYTES_REQUIRED;
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final boolean GENERATE_GC_TRACE = true;

  /* virtual memory resources */
  private static FreeListVMResource losVM;
  private static MonotoneVMResource ss0VM;
  private static MonotoneVMResource ss1VM;

  /* memory resources */
  private static MemoryResource ssMR;
  private static MemoryResource losMR;

  /* large object space (LOS) collector */
  private static TreadmillSpace losSpace;

  /* GC state */
  private static boolean hi = false; // True if allocing to "higher" semispace
  private static boolean traceInducedGC = false; // True if trace triggered GC
  private static boolean deathScan = false;
  private static boolean finalDead = false;

  /* Tracing stuff */
  protected static MemoryResource traceMR;
  private static MonotoneVMResource traceVM;
  protected static RawPageAllocator traceRPA;

  /* Allocators */
  private static final byte LOW_SS_SPACE = 0;
  private static final byte HIGH_SS_SPACE = 1;
  public static final byte DEFAULT_SPACE = 2; // logical space that maps to either LOW_SS_SPACE or HIGH_SS_SPACE
  private static final byte TRACE_SPACE = 3;

  /* Miscellaneous constants */
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  
  /* Memory layout constants */
  private static final Extent      TRACE_SIZE = META_DATA_SIZE;
  private static final Address    TRACE_START = PLAN_START;
  private static final Address      TRACE_END = TRACE_START.add(TRACE_SIZE);
  public  static final long         AVAILABLE = Memory.MAXIMUM_MAPPABLE.diff(TRACE_END).toLong();
  private static final Extent         SS_SIZE = Conversions.roundDownVM(Extent.fromIntZeroExtend((int)(AVAILABLE / 2.3)));
  private static final Extent        LOS_SIZE = Conversions.roundDownVM(Extent.fromIntZeroExtend((int)(AVAILABLE / 2.3 * 0.3)));
  public  static final Extent        MAX_SIZE = SS_SIZE.add(SS_SIZE);

  private static final Address      LOS_START = TRACE_END;
  private static final Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final Address       SS_START = LOS_END;
  private static final Address   LOW_SS_START = SS_START;
  private static final Address  HIGH_SS_START = SS_START.add(SS_SIZE);
  private static final Address         SS_END = HIGH_SS_START.add(SS_SIZE);
  private static final Address       HEAP_END = SS_END;


  /****************************************************************************
   *
   * Instance variables
   */

  /* Allocators */
  public BumpPointer ss;
  private TreadmillLocal los;

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
    /* Setup the "off-book" space for tracing */
    traceMR = new MemoryResource("trace", META_DATA_POLL_FREQUENCY);
    traceVM = new MonotoneVMResource(TRACE_SPACE, "Trace data", traceMR,
                                      TRACE_START, TRACE_SIZE,
                                      VMResource.META_DATA);
    traceRPA = new RawPageAllocator(traceVM, traceMR);
    SortTODSharedDeque workList = new SortTODSharedDeque(traceRPA, 1);
    SortTODSharedDeque traceBuf = new SortTODSharedDeque(traceRPA, 1); 
    TraceGenerator.init(workList, traceBuf);

    /* Now initialize the normal program heap */
    ssMR = new MemoryResource("ss", POLL_FREQUENCY);
    losMR = new MemoryResource("los", POLL_FREQUENCY);
    ss0VM = new MonotoneVMResource(LOW_SS_SPACE, "Lower SS", ssMR, LOW_SS_START,
				   SS_SIZE, VMResource.MOVABLE);
    ss1VM = new MonotoneVMResource(HIGH_SS_SPACE, "Upper SS", ssMR, 
				   HIGH_SS_START, SS_SIZE, VMResource.MOVABLE);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, 
				   VMResource.IN_VM);
    losSpace = new TreadmillSpace(losVM, losMR);

    addSpace(LOW_SS_SPACE, "Lower Semi-Space");
    addSpace(HIGH_SS_SPACE, "Upper Semi-Space");
    addSpace(LOS_SPACE, "LOS Space");
    addSpace(TRACE_SPACE, "Trace Space");
    /* DEFAULT_SPACE is logical and does not actually exist */
  }

  /**
   * Constructor
   */
  public GCTrace() {
    ss = new BumpPointer(ss0VM);
    los = new TreadmillLocal(losSpace);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot() { 
    StopTheWorldGC.boot();
  }

  /**
   * The planExit method is called at RVM termination to allow the trace process
   * to finish.
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
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment.
   * @param offset The alignment offset.
   * @param allocator The allocator number to be used for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final Address alloc(int bytes, int align, int offset, int allocator)
    throws InlinePragma {
    switch (allocator) {
    case  DEFAULT_SPACE: return ss.alloc(bytes, align, offset);
    case IMMORTAL_SPACE: return immortal.alloc(bytes, align, offset);
    case      LOS_SPACE: return los.alloc(bytes, align, offset);
    default: 
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
      return Address.zero();
    }
  }

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
    case  DEFAULT_SPACE: break;
    case IMMORTAL_SPACE: ImmortalSpace.postAlloc(object); break;
    case      LOS_SPACE: losSpace.initializeHeader(object); break;
    default:
      if (Assert.VERIFY_ASSERTIONS) Assert.fail("No such allocator"); 
    }
    /* Now have the trace process aware of the new allocation. */
    traceInducedGC = TraceGenerator.MERLIN_ANALYSIS;
    TraceGenerator.traceAlloc(allocator == IMMORTAL_SPACE, object, typeRef, bytes);
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
    Assert._assert(bytes <= LOS_SIZE_THRESHOLD);
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

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == ss) return DEFAULT_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return ss;
    if (s == LOS_SPACE) return los;
    return super.getAllocatorFromSpace(s);
  }
  
  /**
   * Give the compiler/runtime statically generated alloction advice
   * which will be passed to the allocation routine at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return Allocation advice to be passed to the allocation routine
   * at runtime
   */
  public final AllocAdvice getAllocAdvice(MMType type, int bytes,
					  CallSite callsite,
					  AllocAdvice hint) {
    return null;
  }

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
   * @param mustCollect True if a this collection is forced.
   * @param mr The memory resource that triggered this collection.
   * @return True if a collection is triggered
   */
  public final boolean poll(boolean mustCollect, MemoryResource mr) 
    throws LogicallyUninterruptiblePragma {
    if (collectionsInitiated > 0 || !initialized || mr == metaDataMR) 
      return false;
    mustCollect |= stressTestGCRequired();
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      required = mr.reservedPages() - mr.committedPages();
      if (mr == ssMR) required = required<<1; // must account for copy reserve
      traceInducedGC = false;
      Collection.triggerCollection(Collection.RESOURCE_GC_TRIGGER);
      return true;
    }
    return false;
  }

  /****************************************************************************
   *
   * Collection
   */

  /* Important notes:
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
    if (!traceInducedGC) {
      hi = !hi;        // flip the semi-spaces
      ssMR.reset();    // reset the semispace memory resource, and
      /* prepare each of the collected regions */
      CopySpace.prepare(((hi) ? ss0VM : ss1VM), ssMR);
      ImmortalSpace.prepare(immortalVM, null);
      losSpace.prepare(losVM, losMR);
    }
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
    if (!traceInducedGC) {
      /* rebind the semispace bump pointer to the appropriate semispace. */
      ss.rebind(((hi) ? ss1VM : ss0VM)); 
      los.prepare();
    }
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
    if (!traceInducedGC) {
      los.release();
    }
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
      losSpace.release();
      ((hi) ? ss0VM : ss1VM).release();
      CopySpace.release(((hi) ? ss0VM : ss1VM), ssMR);
      ImmortalSpace.release(immortalVM, null);
      progress = (getPagesReserved() + required < getTotalPages());
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
   * @param obj The object reference to be traced.  In certain cases, this should
   * <i>NOT</i> be an interior pointer.
   * @return The possibly moved reference.
   */
  public static final Address traceObject(Address obj)
    throws InlinePragma {
    if (obj.isZero()) return obj;
    if (traceInducedGC) {
      TraceGenerator.rootEnumerate(obj);
      return obj;
    } else if (deathScan) {
      TraceGenerator.propagateDeathTime(obj);
      return obj;
    } else {
      return followObject(obj);
    }
  }

  /**
   * Promote a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final Address followObject(Address obj)
    throws InlinePragma {
    Address addr = ObjectModel.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case LOW_SS_SPACE:   return   hi  ? CopySpace.traceObject(obj) : obj;
    case HIGH_SS_SPACE:  return (!hi) ? CopySpace.traceObject(obj) : obj;
    case LOS_SPACE:      return losSpace.traceObject(obj);
    case IMMORTAL_SPACE: return ImmortalSpace.traceObject(obj);
    case BOOT_SPACE:     return ImmortalSpace.traceObject(obj);
    case META_SPACE:     return obj;
    default:  
      if (Assert.VERIFY_ASSERTIONS) 
	spaceFailure(obj, space, "Plan.traceObject()");
      return obj;
    }
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final Address traceObject(Address obj, boolean root) {
    return traceObject(obj);  // root or non-root is of no consequence here
  }


  /**
   * Scan an object that was previously forwarded but not scanned.
   * The separation between forwarding and scanning is necessary for
   * the "pre-copying" mechanism to function properly.
   *
   * @param object The object to be scanned.
   */
  protected final void scanForwardedObject(Address object) {
    Scan.scanObject(object);
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
      Address obj = location.loadAddress();
      if (!obj.isZero()) {
        TraceGenerator.rootEnumerate(obj);
      }
    } else {
      Address obj = location.loadAddress();
      if (!obj.isZero()) {
        Address addr = ObjectModel.refToAddress(obj);
        byte space = VMResource.getSpace(addr);
        if ((hi && space == LOW_SS_SPACE) || (!hi && space == HIGH_SS_SPACE))
          location.store(CopySpace.forwardObject(obj));
      }
    }
  }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.
   */
  public static final Address getForwardedReference(Address object) {
    if (!object.isZero()) {
      Address addr = ObjectModel.refToAddress(object);
      byte space = VMResource.getSpace(addr);
      if ((hi && space == LOW_SS_SPACE) || (!hi && space == HIGH_SS_SPACE)) {
        Assert._assert(CopySpace.isForwarded(object));
        return CopySpace.getForwardingPointer(object);
      }
    }
    return object;
  }
  
  /**
   * Return true if the given reference is to an object that is within
   * one of the semi-spaces.
   *
   * @param ref The object in question
   * @return True if the given reference is to an object that is within
   * one of the semi-spaces.
   */
  public static final boolean isSemiSpaceObject(Address ref) {
    if (traceInducedGC) return true;
    Address addr = ObjectModel.refToAddress(ref);
    return (addr.GE(SS_START) && addr.LE(SS_END));
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(Address obj) {
    if (obj.isZero()) return false;
    if (traceInducedGC) return true;
    Address addr = ObjectModel.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case LOW_SS_SPACE:    return CopySpace.isLive(obj);
    case HIGH_SS_SPACE:   return CopySpace.isLive(obj);
    case LOS_SPACE:       return losSpace.isLive(obj);
    case IMMORTAL_SPACE:  return true;
    case BOOT_SPACE:	  return true;
    case META_SPACE:	  return true;
    default:
      if (Assert.VERIFY_ASSERTIONS) spaceFailure(obj, space, "Plan.isLive()");
      return false;
    }
  }

 /**
   * Return true if <code>obj</code> is a reachable object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a reachable object; unreachable objects
   *         may still be live, however
   */
  public final boolean isReachable(Address obj) {
    if (finalDead) return false;
    if (obj.isZero()) return false;
    Address addr = ObjectModel.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
    case LOW_SS_SPACE:    return ((hi) ? CopySpace.isLive(obj) : true);
    case HIGH_SS_SPACE:   return ((!hi) ? CopySpace.isLive(obj) : true);
    case LOS_SPACE:       return losSpace.isLive(obj); 
    }
    return super.isReachable(obj);
  }

  // XXX Missing Javadoc comment.
  public static boolean willNotMove (Address obj) {
   if (traceInducedGC) return true;
   boolean movable = VMResource.refIsMovable(obj);
   if (!movable) return true;
   Address addr = ObjectModel.refToAddress(obj);
   return (hi ? ss1VM : ss0VM).inRange(addr);
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
   * @param locationMetadata an int that encodes the source location being modified
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
   * Return the number of pages reserved for use given the pending
   * allocation.  This <i>includes</i> space reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    /* We must account for the number of pages required for copying,
       which equals the number of semi-space pages reserved */
    return ssMR.reservedPages() + getPagesUsed();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = ssMR.reservedPages();
    pages += losMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  protected static final int getPagesAvail() {
    int semispaceTotal = getTotalPages() - losMR.reservedPages() 
      - immortalMR.reservedPages();
    return (semispaceTotal>>1) - ssMR.reservedPages();
  }

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

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    ss.show();
    los.show();
    immortal.show();
  }
}
