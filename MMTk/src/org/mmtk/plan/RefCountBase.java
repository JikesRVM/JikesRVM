/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.RefCountLOSLocal;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.alloc.BumpPointer;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.Options;
import org.mmtk.utility.scan.*;
import org.mmtk.utility.statistics.*;
import org.mmtk.vm.Memory;
import org.mmtk.vm.Plan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements a base functionality of a simple
 * non-concurrent reference counting collector.
 *
 * $Id$
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class RefCountBase extends StopTheWorldGC implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final int GC_HEADER_BYTES_REQUIRED = RefCountSpace.GC_HEADER_BYTES_REQUIRED;
  public static final boolean REF_COUNT_CYCLE_DETECTION = true;
  public static final boolean SUPPORTS_PARALLEL_GC = false;
  protected static final boolean WITH_COALESCING_RC = true;

  // virtual memory resources
  protected static FreeListVMResource losVM;
  protected static FreeListVMResource rcVM;

  // RC collection space
  protected static RefCountSpace rcSpace;

  // memory resources
  protected static MemoryResource rcMR;

  // shared queues
  protected static SharedDeque decPool;
  protected static SharedDeque modPool;
  protected static SharedDeque rootPool;

  // GC state
  protected static int required;  // how many pages must this GC yeild?
  protected static long timeCap = 0; // time within which this GC should finish

  // Allocators
  public static final byte RC_SPACE = 0;

  // Miscellaneous constants
  protected static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

 // Memory layout constants
  public    static final long           AVAILABLE = Memory.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  protected static final Extent         RC_SIZE = Conversions.roundDownVM(Extent.fromIntZeroExtend((int)(AVAILABLE * 0.6)));
  protected static final Extent        LOS_SIZE = Conversions.roundDownVM(Extent.fromIntZeroExtend((int)(AVAILABLE * 0.2)));
  public    static final Extent       MAX_SIZE = RC_SIZE;
  protected static final Address      RC_START = PLAN_START;
  protected static final Address        RC_END = RC_START.add(RC_SIZE);
  protected static final Address     LOS_START = RC_END;
  protected static final Address       LOS_END = LOS_START.add(LOS_SIZE);

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  public RefCountLocal rc;
  protected RefCountLOSLocal los;

  // queues (buffers)
  protected AddressDeque decBuffer;
  protected AddressDeque modBuffer;
  protected AddressDeque newRootSet;

  // enumerators
  public RCDecEnumerator decEnum;
  protected RCModifiedEnumerator modEnum;
  protected RCSanityEnumerator sanityEnum;

  // counters
  protected static EventCounter wbFast;
  protected static EventCounter wbSlow;


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
    rcMR = new MemoryResource("rc", POLL_FREQUENCY);

    // virtual memory resources
    rcVM = new FreeListVMResource(RC_SPACE, "RC", RC_START, RC_SIZE, VMResource.IN_VM, RefCountLocal.META_DATA_PAGES_PER_REGION);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);

    // collectors
    rcSpace = new RefCountSpace(rcVM, rcMR);
    addSpace(RC_SPACE, "RC Space");

    // instantiate shared queues
    if (WITH_COALESCING_RC) {
      modPool = new SharedDeque(metaDataRPA, 1);
      modPool.newClient();
    }
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
  public RefCountBase() {
    if (WITH_COALESCING_RC) modBuffer = new AddressDeque("mod buf", modPool);
    if (WITH_COALESCING_RC) modEnum = new RCModifiedEnumerator();
    decBuffer = new AddressDeque("dec buf", decPool);
    newRootSet = new AddressDeque("root set", rootPool);
    los = new RefCountLOSLocal(losVM, rcMR);
    rc = new RefCountLocal(rcSpace, los, decBuffer, newRootSet);
    decEnum = new RCDecEnumerator();
    if (RefCountSpace.RC_SANITY_CHECK) sanityEnum = new RCSanityEnumerator(rc);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot() throws InterruptiblePragma {
    StopTheWorldGC.boot();
  }

  /****************************************************************************
   *
   * Allocation
   */

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
   * Flush any remembered sets pertaining to the current collection.
   */
  protected final void flushRememberedSets() {
    if (WITH_COALESCING_RC) processModBufs();
  }

  /**
   * Process the modified object buffers, enumerating each object's
   * fields
   */
  protected final void processModBufs() {
    modBuffer.flushLocal();
    Address obj = Address.zero();
    while (!(obj = modBuffer.pop()).isZero()) {
      RefCountSpace.makeUnlogged(obj);
      Scan.enumeratePointers(obj, modEnum);
    }
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param typeRef the type reference for the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param status the initial value of the status word
   * @return The new value of the status word
   */
  public static Word getBootTimeAvailableBits(int ref, Address typeRef,
                                              int size, Word status)
    throws UninterruptiblePragma, InlinePragma {
    if (WITH_COALESCING_RC) status = status.or(RefCountSpace.UNLOGGED);
    return status;
  }
  
 /****************************************************************************
   *
   * Space management
   */

  /**
   * Return the number of pages consumed by meta data.
   *
   * @return The number of pages consumed by meta data.
   */
  public static final int getMetaDataPagesUsed() {
    return metaDataMR.reservedPages();
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
  public final void enumerateDecrementPointerLocation(Address objLoc)
    throws InlinePragma {
    Address object = objLoc.loadAddress();
    if (Plan.isRCObject(object))
      decBuffer.push(object);
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
  public final void addToDecBuf(Address object)
    throws InlinePragma {
    decBuffer.push(object);
  }

  /**
   * Add an object to the root set
   *
   * @param root The object to be added to root set
   */
  public final void addToRootSet(Address root) 
    throws InlinePragma {
    newRootSet.push(root);
  }

  /****************************************************************************
   *
   * Miscellaneous
   */

  /**
   * Return the cycle time at which this GC should complete.
   *
   * @return The time cap for this GC (i.e. the time by which it
   * should complete).
   */
  public static final long getTimeCap() {
    return timeCap;
  }
}
