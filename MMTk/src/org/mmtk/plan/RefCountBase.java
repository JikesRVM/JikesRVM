/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package org.mmtk.plan;

import org.mmtk.policy.ImmortalSpace;
import org.mmtk.policy.LargeRCObjectLocal;
import org.mmtk.policy.RefCountSpace;
import org.mmtk.policy.RefCountLocal;
import org.mmtk.policy.Space;
import org.mmtk.utility.alloc.AllocAdvice;
import org.mmtk.utility.alloc.Allocator;
import org.mmtk.utility.CallSite;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.heap.*;
import org.mmtk.utility.options.*;
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
public abstract class RefCountBase extends StopTheWorldGC 
  implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  public static final boolean NEEDS_WRITE_BARRIER = true;
  public static final int GC_HEADER_WORDS_REQUIRED = RefCountSpace.GC_HEADER_WORDS_REQUIRED;
  public static final boolean REF_COUNT_CYCLE_DETECTION = true;
  public static final boolean SUPPORTS_PARALLEL_GC = false;
  protected static final boolean WITH_COALESCING_RC = true;

  // shared queues
  protected static SharedDeque decPool;
  protected static SharedDeque modPool;
  protected static SharedDeque rootPool;

  // GC state
  protected static int required;  // how many pages must this GC yeild?
  protected static long timeCap = 0; // time within which this GC should finish

  // Spaces
  protected static RefCountSpace rcSpace = new RefCountSpace("rc", DEFAULT_POLL_FREQUENCY, (float) 0.6);
  protected static final int RC = rcSpace.getDescriptor();

  /****************************************************************************
   *
   * Instance variables
   */

  // allocators
  public RefCountLocal rc;
  protected LargeRCObjectLocal los;

  // queues (buffers)
  protected ObjectReferenceDeque decBuffer;
  protected ObjectReferenceDeque modBuffer;
  protected ObjectReferenceDeque newRootSet;

  // enumerators
  public RCDecEnumerator decEnum;
  protected RCModifiedEnumerator modEnum;
  protected RCSanityEnumerator sanityEnum;

  // counters
  protected static EventCounter wbFast;
  protected static EventCounter wbSlow;

  // options
  public static GCTimeCap gcTimeCap; 


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
    gcTimeCap = new GCTimeCap();
    // instantiate shared queues
    if (WITH_COALESCING_RC) {
      modPool = new SharedDeque(metaDataSpace, 1);
      modPool.newClient();
    }
    decPool = new SharedDeque(metaDataSpace, 1);
    decPool.newClient();
    rootPool = new SharedDeque(metaDataSpace, 1);
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
    if (WITH_COALESCING_RC) modBuffer = new ObjectReferenceDeque("mod buf", modPool);
    if (WITH_COALESCING_RC) modEnum = new RCModifiedEnumerator();
    decBuffer = new ObjectReferenceDeque("dec buf", decPool);
    newRootSet = new ObjectReferenceDeque("root set", rootPool);
    los = new LargeRCObjectLocal(loSpace);
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

  /**
   * Return the space into which an allocator is allocating.  This
   * particular method will match against those spaces defined at this
   * level of the class hierarchy.  Subclasses must deal with spaces
   * they define and refer to superclasses appropriately.  This exists
   * to support {@link BasePlan#getOwnAllocator(Allocator)}.<p>
   *
   * Note that we override <code>los</code>, so we must account for it
   * here (rather than in our superclass).
   *
   * @see BasePlan#getOwnAllocator(Allocator)
   * @param a An allocator
   * @return The space into which <code>a</code> is allocating, or
   * <code>null</code> if there is no space associated with
   * <code>a</code>.
   */
  protected Space getSpaceFromAllocator(Allocator a) {
    if (a == rc) return rcSpace;
    else if (a == los) return loSpace;
    return super.getSpaceFromAllocator(a);
  }

  /**
   * Return the allocator instance associated with a space
   * <code>space</code>, for this plan instance.  This exists
   * to support {@link BasePlan#getOwnAllocator(Allocator)}.<p>
   *
   * Note that we override <code>los</code>, so we must account for it
   * here (rather than in our superclass).
   *
   * @see BasePlan#getOwnAllocator(Allocator)
   * @param space The space for which the allocator instance is desired.
   * @return The allocator instance associated with this plan instance
   * which is allocating into <code>space</code>, or <code>null</code>
   * if no appropriate allocator can be established.
   */
  protected Allocator getAllocatorFromSpace(Space space) {
    if (space == rcSpace) return rc;
    else if (space == loSpace) return los;
    return super.getAllocatorFromSpace(space);
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
    ObjectReference object = ObjectReference.nullReference();
    while (!(object = modBuffer.pop()).isNull()) {
      RefCountSpace.makeUnlogged(object);
      Scan.enumeratePointers(object, modEnum);
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
  public static Word getBootTimeAvailableBits(int ref, ObjectReference typeRef,
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
    return metaDataSpace.reservedPages();
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
   * @param location The address of a reference field with an object
   * being enumerated.
   */
  public final void enumerateDecrementPointerLocation(Address location)
    throws InlinePragma {
    ObjectReference object = location.loadObjectReference();
    if (isRCObject(object))
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
  public final void addToDecBuf(ObjectReference object)
    throws InlinePragma {
    decBuffer.push(object);
  }

  /**
   * Add an object to the root set
   *
   * @param root The object to be added to root set
   */
  public final void addToRootSet(ObjectReference root) 
    throws InlinePragma {
    newRootSet.push(root);
  }

  /**
   * Return true if the object resides within the RC space
   *
   * @param object An object reference
   * @return True if the object resides within the RC space
   */
  public static final boolean isRCObject(ObjectReference object)
    throws InlinePragma {
    if (object.isNull()) 
      return false;
    else return (Space.isInSpace(RC, object) || Space.isInSpace(LOS, object));
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
