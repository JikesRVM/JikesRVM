/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Statistics;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;

/**
 * This class implements a simple non-concurrent reference counting
 * collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  final public static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = true;
  public static final boolean needsPutStaticWriteBarrier = false;
  public static final boolean needsTIBStoreWriteBarrier = false;
  public static final boolean refCountCycleDetection = true;
  public static final boolean movesObjects = false;
  public static final boolean sanityTracing = false;
  private static final boolean inlineWriteBarrier = false;

  // virtual memory resources
  private static FreeListVMResource losVM;
  private static FreeListVMResource rcVM;

  // RC collection space
  private static RefCountSpace rcSpace;

  // memory resources
  private static MemoryResource rcMR;

  // shared queues
  private static SharedQueue incPool;
  private static SharedQueue decPool;
  private static SharedQueue rootPool;

  // GC state
  private static boolean progress = true;  // are we making progress?
  private static int required;  // how many pages must this GC yeild?
  private static int lastRCPages = 0; // pages at end of last GC
  
  // Allocators
  public static final byte RC_SPACE = 0;
  public static final byte LOS_SPACE = 1;
  public static final byte DEFAULT_SPACE = RC_SPACE;

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  private static final int LOS_SIZE_THRESHOLD = 8 * 1024; // largest size supported by MS

  // Memory layout constants
  public  static final long            AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  private static final VM_Extent         RC_SIZE = Conversions.roundDownMB(VM_Extent.fromInt((int)(AVAILABLE * 0.7)));
  private static final VM_Extent        LOS_SIZE = Conversions.roundDownMB(VM_Extent.fromInt((int)(AVAILABLE * 0.3)));
  public  static final VM_Extent        MAX_SIZE = RC_SIZE;

  public  static final VM_Address       RC_START = PLAN_START;
  private static final VM_Address         RC_END = RC_START.add(RC_SIZE);
  private static final VM_Address      LOS_START = RC_END;
  private static final VM_Address        LOS_END = LOS_START.add(LOS_SIZE);
  private static final VM_Address       HEAP_END = LOS_END;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocator
  private RefCountLocal rc;
  private RefCountLOSLocal los;

  // counters
  private int wbFastPathCounter;

  // queues (buffers)
  private AddressQueue incBuffer;
  private AddressQueue decBuffer;
  private AddressQueue rootSet;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    // memory resources
    rcMR = new MemoryResource("rc", POLL_FREQUENCY);

    // virtual memory resources
    rcVM = new FreeListVMResource(RC_SPACE, "RC", RC_START, RC_SIZE, VMResource.IN_VM);
    losVM = new FreeListVMResource(LOS_SPACE, "LOS", LOS_START, LOS_SIZE, VMResource.IN_VM);

    // collectors
    rcSpace = new RefCountSpace(rcVM, rcMR);
    addSpace(RC_SPACE, "RC Space");

    // instantiate shared queues
    incPool = new SharedQueue(metaDataRPA, 1);
    incPool.newClient();
    decPool = new SharedQueue(metaDataRPA, 1);
    decPool.newClient();
    rootPool = new SharedQueue(metaDataRPA, 1);
    rootPool.newClient();
  }

  /**
   * Constructor
   */
  public Plan() {
    incBuffer = new AddressQueue("inc buf", incPool);
    decBuffer = new AddressQueue("dec buf", decPool);
    rootSet = new AddressQueue("root set", rootPool);
    los = new RefCountLOSLocal(losVM, rcMR);
    rc = new RefCountLocal(rcSpace, this, los, incBuffer, decBuffer, rootSet);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc (int bytes, boolean isScalar, int allocator,
				AllocAdvice advice)
    throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(bytes == (bytes & (~(WORD_SIZE-1))));
    if (allocator == DEFAULT_SPACE && bytes > LOS_SIZE_THRESHOLD) {
      return los.alloc(isScalar, bytes);
    } else {
      switch (allocator) {
      case       RC_SPACE: return rc.alloc(isScalar, bytes, false);
      case IMMORTAL_SPACE: return immortal.alloc(isScalar, bytes);
      case      LOS_SPACE: return los.alloc(isScalar, bytes);
      default:
	if (VM_Interface.VerifyAssertions) VM_Interface.sysFail("No such allocator");
	return VM_Address.zero();
      }
    }
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(VM_Address ref, Object[] tib, int bytes,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    switch (allocator) {
    case RC_SPACE: 
    case LOS_SPACE: decBuffer.pushOOL(VM_Magic.objectAsAddress(ref)); return;
    case IMMORTAL_SPACE: 
      rc.postAllocImmortal(VM_Magic.objectAsAddress(ref));
      ImmortalSpace.postAlloc(ref); return;
    default: if (VM_Interface.VerifyAssertions) VM_Interface.sysFail("No such allocator"); return;
    }
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address allocCopy(VM_Address original, int bytes,
				    boolean isScalar) throws VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
    // return VM_Address.zero();  this trips some Intel assembler bug
    return VM_Address.max();
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(VM_Address ref, Object[] tib, int bytes,
			     boolean isScalar) {} // do nothing

  /**
   * Advise the compiler/runtime which allocator to use for a
   * particular allocation.  This should be called at compile time and
   * the returned value then used for the given site at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return The allocator number to be used for this allocation.
   */
  public final int getAllocator(Type type, int bytes, CallSite callsite,
				AllocAdvice hint) {
    return RC_SPACE;
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == rc) return DEFAULT_SPACE;
    if (a == los) return LOS_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return rc;
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
  public final AllocAdvice getAllocAdvice(Type type, int bytes,
					  CallSite callsite,
					  AllocAdvice hint) {
    return null;
  }

  /**
   * Return the initial header value for a newly allocated instance.
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public static final int getInitialHeaderValue(int bytes) 
    throws VM_PragmaInline {
    return rcSpace.getInitialHeaderValue(bytes);
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
    if (gcInProgress) return false;
    if (mustCollect || 
	getPagesReserved() > getTotalPages() ||
	(((rcMR.committedPages() - lastRCPages) > Options.nurseryPages ||
	  metaDataMR.committedPages() > Options.metaDataPages)
	 && initialized)) {
      if (VM_Interface.VerifyAssertions) 
	VM_Interface._assert(mr != metaDataMR);
      required = mr.reservedPages() - mr.committedPages();
      VM_Interface.triggerCollection(VM_Interface.RESOURCE_TRIGGERED_GC);
      return true;
    }
    return false;
  }

  
  ////////////////////////////////////////////////////////////////////////////
  //
  // Collection
  //
  // Important notes:
  //   . Global actions are executed by only one thread
  //   . Thread-local actions are executed by all threads
  //   . The following order is guaranteed by BasePlan, with each
  //     separated by a synchronization barrier.:
  //      1. globalPrepare()
  //      2. threadLocalPrepare()
  //      3. threadLocalRelease()
  //      4. globalRelease()
  //

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    ImmortalSpace.prepare(immortalVM, null);
    rcSpace.prepare();
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
    rc.prepare();
  }

  /**
   * We reset the state for a GC thread that is not participating in
   * this GC
   */
  public final void prepareNonParticipating() {
    threadLocalPrepare(NON_PARTICIPANT);
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
    rc.release();
    if (GATHER_WRITE_BARRIER_STATS) { 
      // This is printed independantly of the verbosity so that any
      // time someone sets the GATHER_WRITE_BARRIER_STATS flags they
      // will know---it will have a noticable performance hit...
      VM_Interface.sysWrite("<GC ",Statistics.gcCount); VM_Interface.sysWrite(" "); 
      VM_Interface.sysWriteInt(wbFastPathCounter); VM_Interface.sysWrite(" wb-fast>\n");
      wbFastPathCounter = 0;
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
    // release each of the collected regions
    rcSpace.release();
    ImmortalSpace.release(immortalVM, null);
    if (verbose > 2) rc.printStats();
    lastRCPages = rcMR.committedPages();
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress)
	VM_Interface.sysFail("Out of memory");
      progress = false;
    } else
      progress = true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject (VM_Address obj) 
    throws VM_PragmaInline {
    return traceObject(obj, false);
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
  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    if (obj.isZero()) return obj;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    if (space == RC_SPACE || space == LOS_SPACE)
      return rcSpace.traceObject(obj, root);
    else if (sanityTracing && (space == BOOT_SPACE || space == IMMORTAL_SPACE))
      return rcSpace.traceBootObject(obj);
    
    // else this is not a rc heap pointer
    return obj;
  }

  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(VM_Address obj) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    if (space == RC_SPACE || space == LOS_SPACE)
      return rcSpace.isLive(obj);
    else if (space == BOOT_SPACE || space == IMMORTAL_SPACE)
      return true;
    else
      return false;
  }

  /**
   * Reset the GC bits in the header word of an object that has just
   * been copied.  This may, for example, involve clearing a write
   * barrier bit.  In this case nothing is required, so the header
   * word is returned unmodified.
   *
   * @param fromObj The original (uncopied) object
   * @param forwardingWord The integer containing the GCbits , which is the GC word
   * of the original object, and typically encodes some GC state as
   * well as pointing to the copied object.
   * @param bytes The size of the copied object in bytes.
   * @return The updated GC word (in this case unchanged).
   */
  public static final int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingWord, int bytes) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);  // not a copying collector!
    return forwardingWord;
  }

  /**
   * A pointer location has been enumerated by ScanObject.  This is
   * the callback method, allowing the plan to perform an action with
   * respect to that location.
   *
   * @param location An address known to contain a pointer.  The
   * location is within the object being scanned by ScanObject.
   */
  public final void enumeratePointerLocation(VM_Address location) {
    VM_Address object = VM_Magic.getMemoryAddress(location);
    if (!object.isZero()) {
      byte space = VMResource.getSpace(object);
      if (space == RC_SPACE || space == LOS_SPACE)
	rc.enumeratePointer(object);
    }
  }

  public static boolean willNotMove (VM_Address obj) {
    return true;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Write barriers. 
  //

  /**
   * A new reference is about to be created by a putfield bytecode.
   * Take appropriate write barrier actions.
   *
   * @param src The address of the object containing the source of a
   * new reference.
   * @param offset The offset into the source object where the new
   * reference resides (the offset is in bytes and with respect to the
   * object address).
   * @param tgt The target of the new reference
   */
  public final void putFieldWriteBarrier(VM_Address src, int offset,
					 VM_Address tgt)
    throws VM_PragmaInline {
    if (inlineWriteBarrier)
      writeBarrier(src.add(offset), tgt);
    else
      writeBarrierOOL(src.add(offset), tgt);
  }

  /**
   * A new reference is about to be created by a aastore bytecode.
   * Take appropriate write barrier actions.
   *
   * @param src The address of the array containing the source of a
   * new reference.
   * @param index The index into the array where the new reference
   * resides (the index is the "natural" index into the array,
   * i.e. a[index]).
   * @param tgt The target of the new reference
   */
  public final void arrayStoreWriteBarrier(VM_Address src, int index,
					   VM_Address tgt)
    throws VM_PragmaInline {
    if (inlineWriteBarrier)
      writeBarrier(src.add(index<<LOG_WORD_SIZE), tgt);
    else
      writeBarrierOOL(src.add(index<<LOG_WORD_SIZE), tgt);
  }

  /**
   * A new reference is about to be created.  Perform appropriate
   * write barrier action.<p>
   *
   * In this case, we remember the address of the source of the
   * pointer if the new reference points into the nursery from
   * non-nursery space.  This method is <b>inlined</b> by the
   * optimizing compiler, and the methods it calls are forced out of
   * line.
   *
   * @param src The address of the word (slot) containing the new
   * reference.
   * @param tgt The target of the new reference (about to become the
   * contents of src).
   */
  private final void writeBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFastPathCounter++;
    VM_Address old;
    do {
      old = VM_Magic.prepareAddress(src, 0);
    } while (!VM_Magic.attemptAddress(src, 0, old, tgt));
    if (old.GE(RC_START))
      decBuffer.pushOOL(old);
    if (tgt.GE(RC_START))
      incBuffer.pushOOL(tgt);
  }

  /**
   * An out of line version of the write barrier.  This method is
   * forced <b>out of line</b> by the optimizing compiler, and the
   * methods it calls are forced out of inline.
   *
   * @param src The address of the word (slot) containing the new
   * reference.
   * @param tgt The target of the new reference (about to become the
   * contents of src).
   */
  private final void writeBarrierOOL(VM_Address src, VM_Address tgt) 
    throws VM_PragmaNoInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFastPathCounter++;
    VM_Address old;
    do {
      old = VM_Magic.prepareAddress(src, 0);
    } while (!VM_Magic.attemptAddress(src, 0, old, tgt));
    if (old.GE(RC_START))
      decBuffer.push(old);
    if (tgt.GE(RC_START))
      incBuffer.push(tgt);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This <i>includes</i> space reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    return getPagesUsed();
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation. 
   *
   * @return The number of pages reserved given the pending
   * allocation.
   */
  protected static final int getPagesUsed() {
    int pages = rcMR.reservedPages();
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
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  public static final int getPagesAvail() {
    return getTotalPages() - getPagesUsed();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // RC methods
  //

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
    rootSet.push(root);
  }

  /**
   * Add an object to the trace buffer (used for sanity tracing)
   *
   * @param object The object to be added to the trace buffer
   */
  public final void addToTraceBuffer(VM_Address object) 
    throws VM_PragmaInline {
    rc.addToTraceBuffer(object);
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    rc.show();
    los.show();
    immortal.show();
  }

  /**
   * Return (as a double) the time at which this GC should complete.
   *
   * @return The time cap for this GC (i.e. the time by which it
   * should complete).
   */
  public static final double getTimeCap() {
    return gcStartTime + ((double) Options.gcTimeCap)/1000;
  }
}

