/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.CopySpace;
import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountLOSLocal;
import org.mmtk.utility.AddressDeque;
import org.mmtk.utility.AllocAdvice;
import org.mmtk.utility.Allocator;
import org.mmtk.utility.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.FreeListVMResource;
import org.mmtk.utility.Log;
import org.mmtk.utility.Memory;
import org.mmtk.utility.MemoryResource;
import org.mmtk.utility.MonotoneVMResource;
import org.mmtk.utility.MMType;
import org.mmtk.utility.Options;
import org.mmtk.utility.RCDecEnumerator;
import org.mmtk.utility.RCModifiedEnumerator;
import org.mmtk.utility.RCSanityEnumerator;
import org.mmtk.utility.Scan;
import org.mmtk.utility.SharedDeque;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.VMResource;
import org.mmtk.vm.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This class implements a generational-reference counting hybrid
 * using the "Ulterior RC" algorithm described by Blackburn and
 * McKinley.<p>
 *
 * See S.M. Blackburn and K.S. McKinley, "Ulterior Reference Counting:
 * Fast Garbage Collection Without A Long Wait", OOPSLA, October 2003.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final boolean MOVES_OBJECTS = true;
  public static final boolean REF_COUNT_CYCLE_DETECTION = true;
  public static final boolean SUPPORTS_PARALLEL_GC = false;
  public static final boolean STEAL_NURSERY_GC_HEADER = false;
  static final boolean WITH_COALESCING_RC = true;

  // virtual memory regions
  private static MonotoneVMResource nurseryVM;
  private static FreeListVMResource losVM;
  private static FreeListVMResource rcVM;

  // RC collection space
  private static RefCountSpace rcSpace;

  // memory resources
  private static MemoryResource nurseryMR;
  private static MemoryResource rcMR;

  // shared queues
  private static SharedDeque decPool;
  private static SharedDeque modPool;
  private static SharedDeque rootPool;

  // GC state
  private static int required;  // how many pages must this GC yeild?
  private static int previousMetaDataPages;  // meta-data pages after last GC
  private static long timeCap = 0; // time within which this GC should finish

  // Allocators
  public static final byte NURSERY_SPACE = 0;
  public static final byte RC_SPACE = 1;
  public static final byte DEFAULT_SPACE = NURSERY_SPACE;

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  // Memory layout constants
  public    static final long           AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  protected static final VM_Extent    MATURE_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE * 0.66)));
  protected static final VM_Extent   NURSERY_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(AVAILABLE * 0.33)));
  private static final VM_Extent          RC_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(MATURE_SIZE.toLong() * 0.7)));
  private static final VM_Extent         LOS_SIZE = Conversions.roundDownMB(VM_Extent.fromIntZeroExtend((int)(MATURE_SIZE.toLong() * 0.3)));
  public    static final VM_Extent       MAX_SIZE = MATURE_SIZE;
  protected static final VM_Address      RC_START = PLAN_START;
  protected static final VM_Address        RC_END = RC_START.add(RC_SIZE);
  protected static final VM_Address     LOS_START = RC_END;
  protected static final VM_Address       LOS_END = LOS_START.add(LOS_SIZE);
  protected static final VM_Address NURSERY_START = LOS_END;
  protected static final VM_Address   NURSERY_END = NURSERY_START.add(NURSERY_SIZE);
  protected static final VM_Address      HEAP_END = NURSERY_END;

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  private BumpPointer nursery;
  private RefCountLocal rc;
  private RefCountLOSLocal los;

  // queues (buffers)
  private AddressDeque decBuffer;
  private AddressDeque modBuffer;
  private AddressDeque newRootSet;

  // enumerators
  public RCDecEnumerator decEnum;
  private RCModifiedEnumerator modEnum;
  private RCSanityEnumerator sanityEnum;

  // counters
  private static EventCounter wbFast;
  private static EventCounter wbSlow;
  private int incCounter;
  private int decCounter;
  private int modCounter;

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time). This is where key <i>global</i> instances
   * are allocated.  These instances will be incorporated into the
   * boot image by the build process.
   */
  static {
    // memory resources
    nurseryMR = new MemoryResource("nursery", POLL_FREQUENCY);
    rcMR = new MemoryResource("rc", POLL_FREQUENCY);

    // virtual memory resources
    nurseryVM  = new MonotoneVMResource(NURSERY_SPACE, "Nursery", nurseryMR, NURSERY_START, NURSERY_SIZE, VMResource.MOVABLE);
    rcVM       = new FreeListVMResource(RC_SPACE, "RC", RC_START, RC_SIZE, VMResource.IN_VM);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);

    // collectors
    rcSpace = new RefCountSpace(rcVM, rcMR);
    addSpace(RC_SPACE, "RC Space");

    // instantiate shared queues
    modPool = new SharedDeque(metaDataRPA, 1);
    modPool.newClient();
    decPool = new SharedDeque(metaDataRPA, 1);
    decPool.newClient();
    rootPool = new SharedDeque(metaDataRPA, 1);
    rootPool.newClient();

    if (GATHER_WRITE_BARRIER_STATS) {
      wbFast = new EventCounter("wbFast");
      wbSlow = new EventCounter("wbSlow");
    }
  }

  /**
   * Constructor
   */
  public Plan() {
    nursery = new BumpPointer(nurseryVM);
    modBuffer = new AddressDeque("mod buf", modPool);
    modEnum = new RCModifiedEnumerator(this);
    decBuffer = new AddressDeque("dec buf", decPool);
    newRootSet = new AddressDeque("root set", rootPool);
    los = new RefCountLOSLocal(losVM, rcMR);
    rc = new RefCountLocal(rcSpace, this, los, decBuffer, newRootSet);
    decEnum = new RCDecEnumerator(this);
    if (RefCountSpace.RC_SANITY_CHECK) {
      sanityEnum = new RCSanityEnumerator(rc);
    }
 }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }

  /****************************************************************************
   *
   * Allocation
   */

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param align The requested alignment
   * @param offset The alignment offset
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc(int bytes, int align, int offset, int allocator)
    throws VM_PragmaInline {
    if (STEAL_NURSERY_GC_HEADER
	&& allocator == NURSERY_SPACE) {
      // this assertion is unguarded so will even fail in FastAdaptive!
      // we need to abstract the idea of stealing nursery header bytes,
      // but we want to wait for the forward object model first...
      VM_Interface._assert(false);
    }
    switch (allocator) {
    case  NURSERY_SPACE: return nursery.alloc(bytes, align, offset);
    case       RC_SPACE: return rc.alloc(bytes, align, offset, false);
    case IMMORTAL_SPACE: return immortal.alloc(bytes, align, offset);
    case      LOS_SPACE: return los.alloc(bytes, align, offset);
    default:
      if (VM_Interface.VerifyAssertions) 
	VM_Interface.sysFail("No such allocator");
      return VM_Address.zero();
    }
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(VM_Address ref, Object[] tib, int bytes,
                              int allocator)
    throws VM_PragmaInline {
    switch (allocator) {
    case NURSERY_SPACE: return;
    case RC_SPACE:
    case LOS_SPACE:
      modBuffer.push(ref);
      Header.initializeRCHeader(ref, tib, bytes, true);
      decBuffer.push(ref);
      if (RefCountSpace.RC_SANITY_CHECK) {
        RefCountLocal.sanityAllocCount(ref); 
      }
      return;
    case IMMORTAL_SPACE: 
      if (RefCountSpace.RC_SANITY_CHECK) rc.addImmortalObject(ref);
      modBuffer.push(ref);
      return;
    default:
      if (VM_Interface.VerifyAssertions)
        VM_Interface.sysFail("No such allocator");
      return;
    } 
  }

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
  public final VM_Address allocCopy(VM_Address original, int bytes,
                                    int align, int offset) throws VM_PragmaInline {
    return rc.alloc(bytes, align, offset, false);  // FIXME is this right???
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   */
  public final void postCopy(VM_Address ref, Object[] tib, int bytes)
    throws VM_PragmaInline {
    CopyingHeader.clearGCBits(ref);
    Header.initializeRCHeader(ref, tib, bytes, false);
    Header.makeUnlogged(ref);
    if (RefCountSpace.RC_SANITY_CHECK) {
      RefCountLocal.sanityAllocCount(ref); 
    }
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == nursery) return DEFAULT_SPACE;
    if (a == rc) return RC_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return nursery;
    if (s == RC_SPACE) return rc;
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
   * Return the initial header value for a newly allocated LOS
   * instance.
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public static VM_Word getInitialHeaderValue(int size) {
    return rcSpace.getInitialHeaderValue(size);
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
    throws VM_PragmaLogicallyUninterruptible {
    if (collectionsInitiated > 0 || !initialized) return false;
    mustCollect |= stressTestGCRequired();
    boolean heapFull = getPagesReserved() > getTotalPages();
    boolean nurseryFull = nurseryMR.reservedPages() > Options.maxNurseryPages;
    int newMetaDataPages = metaDataMR.committedPages() - previousMetaDataPages;
    if (mustCollect || heapFull || nurseryFull ||
        (progress && (newMetaDataPages > Options.metaDataPages))) {
      if (mr == metaDataMR) {
        awaitingCollection = true;
        return false;
      }
      required = mr.reservedPages() - mr.committedPages();
      if (mr == nurseryMR) required = required<<1;  // account for copy reserve
      VM_Interface.triggerCollection(VM_Interface.RESOURCE_GC_TRIGGER);
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
    timeCap = VM_Interface.cycles() + VM_Interface.millisToCycles(Options.gcTimeCap);
    nurseryMR.reset();
    rcSpace.prepare();
    ImmortalSpace.prepare(immortalVM, null);
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
    rc.prepare(Options.verboseTiming && count==1);
    nursery.rebind(nurseryVM);
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the mark-sweep collector used by the
   * LOS).
   */
  protected final void threadLocalRelease(int count) {
    rc.release(count, Options.verboseTiming && count==1);
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
    nurseryVM.release();
    rcSpace.release();
    ImmortalSpace.release(immortalVM, null);
    if (Options.verbose > 2) rc.printStats();
    progress = (getPagesReserved() + required < getTotalPages());
    previousMetaDataPages = metaDataMR.committedPages();
  }

  /**
   * Flush any remembered sets pertaining to the current collection.
   */
  protected final void flushRememberedSets() {
    processModBufs();
  }

  /****************************************************************************
   *
   * Object processing and tracing
   */

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject (VM_Address object) 
    throws VM_PragmaInline {
    return traceObject(object, false);
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
  public static final VM_Address traceObject(VM_Address object, boolean root) {
    if (object.isZero()) return object;
    VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END)) {
      if (RefCountSpace.RC_SANITY_CHECK && root) 
        VM_Interface.getPlan().rc.incSanityTraceRoot(object);
      if (addr.GE(NURSERY_START)) {
        VM_Address rtn = CopySpace.traceObject(object);
        // every incoming reference to the from-space object must inc the
        // ref count of forwarded (to-space) object...
        if (root) {
          if (RefCountSpace.INC_DEC_ROOT) {
            RCBaseHeader.incRC(rtn);
            VM_Interface.getPlan().addToRootSet(rtn);
          } else if (RCBaseHeader.setRoot(rtn)) {
            VM_Interface.getPlan().addToRootSet(rtn);
          }
        } else
          RCBaseHeader.incRC(rtn);
        return rtn;
      } else if (addr.GE(RC_START)) {
        if (root)
          return rcSpace.traceObject(object);
        else
          RCBaseHeader.incRC(object);
      }
    }
    // else this is not a rc heap pointer
    return object;
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
  public final void incSanityTrace(VM_Address object, VM_Address location,
                                   boolean root) {
    VM_Address addr = VM_Interface.refToAddress(object);
    VM_Address oldObject = object;

    // if nursery, then get forwarded RC object
    if (addr.GE(NURSERY_START)) {
      if (VM_Interface.VerifyAssertions) 
        VM_Interface._assert(CopyingHeader.isForwarded(object));        
      object = CopyingHeader.getForwardingPtr(object);
      addr = VM_Interface.refToAddress(object);
    }

    if (addr.GE(RC_START)) {
      if (RCBaseHeader.incSanityRC(object, root)) {
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(addr.LT(NURSERY_START));
        Scan.enumeratePointers(object, sanityEnum);
      }
    } else if (RCBaseHeader.markSanityRC(object)) {
      Scan.enumeratePointers(object, sanityEnum);
    } else if (object.EQ(VM_Address.fromIntZeroExtend(0x43080334))) {
      Log.writeln("scanned by marked already!");
    }
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
   * @param root <code>true</code> if the object is being traced
   * directly from a root.
   */
  public final void checkSanityTrace(VM_Address object, VM_Address location) {
    VM_Address addr = VM_Interface.refToAddress(object);
    VM_Address oldObject = object;

    // if nursery, then get forwarded RC object
    if (addr.GE(NURSERY_START)) {
      if (VM_Interface.VerifyAssertions) 
        VM_Interface._assert(CopyingHeader.isForwarded(object));        
      object = CopyingHeader.getForwardingPtr(object);
      addr = VM_Interface.refToAddress(object);
    }

   if (addr.GE(RC_START)) {
     if (RCBaseHeader.checkAndClearSanityRC(object)) {
       Scan.enumeratePointers(object, sanityEnum);
       rc.addLiveSanityObject(object);
     }
   } else if (RCBaseHeader.unmarkSanityRC(object)) {
     Scan.enumeratePointers(object, sanityEnum);
   }
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
  public static void forwardObjectLocation(VM_Address location) 
    throws VM_PragmaInline {
    VM_Address object = VM_Magic.getMemoryAddress(location);
    VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END) && addr.GE(NURSERY_START)) {
      if (VM_Interface.VerifyAssertions) 
        VM_Interface._assert(!object.isZero());
      VM_Magic.setMemoryAddress(location, CopySpace.forwardObject(object));
    }
  }

  /**
   * Scan an object that was previously forwarded but not scanned.
   * The separation between forwarding and scanning is necessary for
   * the "pre-copying" mechanism to function properly.
   *
   * @param object The object to be scanned.
   */
  protected final void scanForwardedObject(VM_Address object) {
    Scan.scanObject(object);
    if (RefCountSpace.INC_DEC_ROOT) {
      RCBaseHeader.incRC(object);
      addToRootSet(object);
    } else if (RCBaseHeader.setRoot(object)) {
      addToRootSet(object);
    }
 }

  /**
   * If the object in question has been forwarded, return its
   * forwarded value.<p>
   *
   * @param object The object which may have been forwarded.
   * @return The forwarded value for <code>object</code>.
   */
  public static final VM_Address getForwardedReference(VM_Address object) {
    VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END) && addr.GE(NURSERY_START)) {
      if (VM_Interface.VerifyAssertions) 
        VM_Interface._assert(CopyingHeader.isForwarded(object));
      return CopyingHeader.getForwardingPointer(object);
    } else
      return object;
  }

  /**
   * Return true if <code>object</code> is a live object.
   *
   * @param object The object in question
   * @return True if <code>object</code> is a live object.
   */
  public static final boolean isLive(VM_Address object) {
    VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
        return CopySpace.isLive(object);
      else if (addr.GE(RC_START))
        return RCBaseHeader.isLiveRC(object);
      else if (addr.GE(BOOT_START))
        return true;
    }
    return false;
  }

  /**
   * Return true if an object is ready to move to the finalizable
   * queue, i.e. it has no regular references to it.
   *
   * @param object The object being queried.
   * @return <code>true</code> if the object has no regular references
   * to it.
   */
  public static final boolean isFinalizable(VM_Address object) {
    VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
        return !CopySpace.isLive(object);
      else if (addr.GE(RC_START))
        return RCBaseHeader.isFinalizable(object);
      else if (addr.GE(BOOT_START))
        return false;
    }
    return false;
  }

  /**
   * An object has just been moved to the finalizable queue.  No need
   * to forward because no copying is performed in this GC, but should
   * clear the finalizer bit of the object so that its reachability
   * now is soley determined by the finalizer queue from which it is
   * now reachable.
   *
   * @param object The object being queried.
   * @return The object (no copying is performed).
   */
  public static VM_Address retainFinalizable(VM_Address object) {
    VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
        return CopySpace.traceObject(object);
      else if (addr.GE(RC_START))
        RCBaseHeader.clearFinalizer(object);
    }
    return object;
  }

  public static boolean willNotMove (VM_Address object) {
   VM_Address addr = VM_Interface.refToAddress(object);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(NURSERY_START))
        return nurseryVM.inRange(addr);
    }
    return true;
  }


  /****************************************************************************
   *
   * Write barriers. 
   */

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.
   *
   * @param src The object into which the new reference will be stored
   * @param slot The address into which the new reference will be
   * stored.
   * @param tgt The target of the new reference
   * @param metaDataA An int that assists the host VM in creating a store 
   * @param metaDataB An int that assists the host VM in creating a store 
   * @param mode The mode of the store (eg putfield, putstatic etc)
   */
  public final void writeBarrier(VM_Address src, VM_Address slot, 
                                 VM_Address tgt, int metaDataA, int metaDataB, int mode) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (Header.needsToBeLogged(src))
      writeBarrierSlow(src);
    VM_Interface.performWriteInBarrier(src, slot, tgt, metaDataA, metaDataB, mode);
  }

  /**
   * A number of references are about to be copied from object
   * <code>src</code> to object <code>dst</code> (as in an array
   * copy).  Thus, <code>dst</code> is the mutated object.  Take
   * appropriate write barrier actions.<p>
   *
   * In this case, we simply remember the mutated source object.
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
  public final boolean writeBarrier(VM_Address src, int srcOffset,
				    VM_Address dst, int dstOffset,
				    int bytes) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFast.inc();
    if (Header.needsToBeLogged(dst))
      writeBarrierSlow(dst);
    return false;
  }

  /**
   * This object <i>may</i> need to be logged because we <i>may</i>
   * have been the first to update it.  We can't be sure because of
   * the (delibrate) lack of synchronization in the
   * <code>needsToBeLogged()</code> method, which can generate a race
   * condition.  So, we now use an atomic operation to arbitrate the
   * race.  If we successful, we will log the object, enumerating its
   * pointers with the decrement enumerator and marking it as logged.
   *
   * @param src The object being mutated.
   */
  private final void writeBarrierSlow(VM_Address src) 
    throws VM_PragmaNoInline {
    if (VM_Interface.VerifyAssertions)
      VM_Interface._assert(!isNurseryObject(src));
    if (Header.attemptToLog(src)) {
      if (GATHER_WRITE_BARRIER_STATS) wbSlow.inc();
      modBuffer.push(src);
      Scan.enumeratePointers(src, decEnum);
      Header.makeLogged(src);
    }
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
    return getPagesUsed() + nurseryMR.reservedPages();  // copy reserve
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
    int pages = nurseryMR.reservedPages();
    pages += rcMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages consumed by meta data.
   *
   * @return The number of pages consumed by meta data.
   */
  public static final int getMetaDataPagesUsed() {
    return metaDataMR.reservedPages();
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the nursery</i>.
   */
  public static int getPagesAvail() {
    int nurseryTotal = getTotalPages() - rcMR.reservedPages() - immortalMR.reservedPages() - metaDataMR.reservedPages();
    return (nurseryTotal>>1) - nurseryMR.reservedPages();
  }

  /**
   * Process the modified object buffers, enumerating each object's
   * fields
   */
  private final void processModBufs() {
    modBuffer.flushLocal();
    VM_Address obj = VM_Address.zero();
    while (!(obj = modBuffer.pop()).isZero()) {
      Header.makeUnlogged(obj);
      Scan.enumeratePointers(obj, modEnum);
    }
  }

  /****************************************************************************
   *
   * Pointer enumeration
   */

  /**
   * A field of an object is being enumerated by ScanObject as part of
   * a recursive decrement (when an object dies, its referent objects
   * must have their counts decremented).  If the field points to the
   * RC space, decrement the count for the referent.
   *
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateDecrementPointerLocation(VM_Address objLoc)
    throws VM_PragmaInline {
    VM_Address object = VM_Magic.getMemoryAddress(objLoc);
    if (isRCObject(object)) {
      decBuffer.push(object);
    }
  }

  /**
   * A field of an object rememebered in the modified objects buffer
   * is being enumerated by ScanObject.  If the field points to the
   * nursery, then add the field address to the locations buffer.  If
   * the field points to the RC space, increment the count of the
   * referent object.
   *
   * @param objLoc The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateModifiedPointerLocation(VM_Address objLoc)
    throws VM_PragmaInline {
    VM_Address object = VM_Magic.getMemoryAddress(objLoc);
    if (!object.isZero()) {
      VM_Address addr = VM_Interface.refToAddress(object);
      if (addr.GE(NURSERY_START)) {
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(addr.LE(NURSERY_END));
        remset.push(objLoc);
      } else if (addr.GE(RC_START))
        RCBaseHeader.incRC(object);
    }
  }

  /****************************************************************************
   *
   * RC methods
   */

  /**
   * Add an object to the decrement buffer
   *
   * @param object The object to be added to the decrement buffer
   */
  public final void addToDecBuf(VM_Address object)
    throws VM_PragmaInline {
    decBuffer.push(object);
  }

  /**
   * Add an object to the root set
   *
   * @param root The object to be added to root set
   */
  public final void addToRootSet(VM_Address root) 
    throws VM_PragmaInline {
    newRootSet.push(root);
  }


  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    nursery.show();
    rc.show();
    los.show();
    immortal.show();
  }

  /**
   * Return the cycle time at which this GC should complete.
   *
   * @return The time cap for this GC (i.e. the time by which it
   * should complete).
   */
  public static final long getTimeCap() {
    return timeCap;
  }

  /**
   * Return true if the object resides within the RC space
   *
   * @param object An object reference
   * @return True if the object resides within the RC space
   */
  public static final boolean isRCObject(VM_Address object)
    throws VM_PragmaInline {
    VM_Address addr = VM_Interface.refToAddress(object);
    return addr.GE(RC_START) && addr.LT(NURSERY_START);
  }

  /**
   * Return true if the object resides within the nursery
   *
   * @param object An object reference
   * @return True if the object resides within the nursery
   */
  static final boolean isNurseryObject(VM_Address object)
    throws VM_PragmaInline {
    if (object.isZero()) 
      return false;
    else {
      VM_Address addr = VM_Interface.refToAddress(object);
      if (addr.GE(NURSERY_START)) {
        if (VM_Interface.VerifyAssertions)
          VM_Interface._assert(addr.LT(NURSERY_END));
        return true;
      } else
        return false;
    }
  }
}
