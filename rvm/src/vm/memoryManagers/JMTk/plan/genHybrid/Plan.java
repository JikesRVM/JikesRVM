/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * All rights reserved.
 */
/**
 * This class implements a simple hybrid copying/mark-sweep
 * generational collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class Plan implements Constants extends BasePlan {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public static methods (aka "class methods")
  //
  // Static methods and fields of Plan are those with global scope,
  // such as virtual memory and memory resources.  This stands in
  // contrast to instance methods which are for fast, unsychronized
  // access to thread-local structures such as bump pointers and
  // remsets.
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    // virtual memory resources
    nurseryVM = new VMResource(NURSERY_START, NURSERY_VM_SIZE);
    msVM = new VMResource(MARKSWEEP_START, MARKSWEEP_VM_SIZE);
    immortalVM = new VMResource(IMMORTAL_START, IMMORTAL_VM_SIZE);
    remsetVM = new VMResouce (REMSET_START, REMSET_VM_SIZE);

    // memory resources
    nurseryMR = new MemoryResource();
    msMR = new MemoryResource();
    immortalMR = new MemoryResource();
    remsetMR = new MemoryResource();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  // Instances of Plan map 1:1 to "kernel threads" (aka CPUs or in
  // Jikes RVM, VM_Processors).  Thus instance methods allow fast,
  // unsychronized access to Plan utilities such as allocation and
  // collection.  Each instance rests on static resources (such as
  // memory and virtual memory resources) which are "global" and
  // therefore "static" members of Plan.
  //

  /**
   * Constructor
   */
  Plan() {
    nursery = new AllocatorBumpPointer(nurseryVM, nurseryMR);
    ms = new AllocatorLea(msVM, msMR);
    immortal = new AllocatorBumpPointer(immortalVM, immortalMR);
    remset = new RemsetSSB(remsetVM, remsetMR);
  }

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public Address alloc(int allocator, EXTENT bytes, boolean isScalar,
		       AllocAdvice advice) {
    if ((allocator == NURSERY_ALLOCATOR) && (bytes.LT(LOS_SIZE_THRESHOLD)))
      return nursery.alloc(isScalar, bytes);
    else if (allocator == IMMORTAL_ALLOCATOR)
      return immortal.alloc(isScalar, bytes);
    else
      return ms.alloc(isScalar, bytes);
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
  public Address allocCopy(Address original, EXTENT bytes, boolean isScalar) {
    return ms.alloc(isScalar, bytes);
  }

  /**
   * Perform a write barrier operation for this collector.
   *
   * @param srcObj The address of the object containing the pointer to
   * be written to
   * @param srcSlot The address of the pointer to be written to
   * @param tgt The address to which the source will point
   */
  public final void writeBarrier(Address srcObj, Address srcSlot, Address tgt){
    // A very inefficient write barrier (assume nursery is in high memory)
    if (srcSlot.LT(NURSERY_START) && tgt.GE(NURSERY_START))
      remset.remember(srcSlot);
  }

  /**
   * Advise the compiler/runtime which allocator to use for a
   * particular allocation.  This should be called at compile time and
   * the returned value then used for the given site at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @return The allocator number to be used for this allocation.
   */
  public int getAllocator(TypeID type, EXTENT bytes, CallSite callsite) {
    if (bytes.GE(LOS_SIZE_THRESHOLD))
      return MS_ALLOCATOR;
    else
      return NURSERY_ALLOCATOR;
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
  public AllocAdvice getAllocAdvice(TypeID type, EXTENT bytes, 
				    CallSite callsite, AllocAdvice hint) {
    return null;
  }

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a block is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of blocks in use (committed for use)
   * beyond the number of blocks available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.
   *
   */
  public void poll() {
    if (getBlocksReserved() > getHeapBlocks())
      MM.triggerCollection();
  }
  
  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param reference The reference to be traced.  The corresponding
   * object is implicitly reachable (live).
   */
  public void traceReference(Address reference) {
    if (reference.LE(HEAP_END)) {
      if (reference.GE(NURSERY_START))
	CollectorCopying.traceReference(reference);
      else if (fullHeapGC) {
	if (reference.GE(MARKSWEEK_START))
	  CollectorLea.traceReference(reference);
	else if (reference.GE(IMMORTAL_START))
	  CollectorImmortal.traceReference(reference);
      }
    } // else this is not a heap pointer
  }

  /**
   * Perform a collection (either minor or full-heap, depending on the
   * size of the nursery).
   */
  public void collect() {
    prepare();
    if (fullHeapGC)
      collect(null);
    else
      collect(remset);
    release();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private methods
  //

  /**
   * Return the number of blocks reserved for use.
   *
   * @return The number of blocks reserved given the pending allocation
   */
  private int getBlocksReserved() {
    int blocks;

    blocks = nurseryMR.reservedBlocks();
    // we must account for the worst case number of blocks required
    // for copying, which equals the number of nursery blocks in
    // use plus a fudge factor which accounts for fragmentation
    blocks += blocks + COPY_FUDGE_BLOCKS;
    
    blocks += msMR.reservedBlocks();
    blocks += immortalMR.reservedBlocks();
    blocks += remsetMR.reservedBlocks();

    return blocks;
  }

  /**
   * Prepare for a collection.
   */
  private void prepare() {
    if (Synchronize.getBarrier()) {
      if (nurseryMR.reservedBlocks() <= MIN_NURSERY_SIZE) 
	fullHeapGC = true;
      
      // prepare each of the collected regions
      CollectorCopying.prepare(nurseryVM, nurseryMR);
      if (fullHeapGC) {
	CollectorLea.prepare(msVM, msMR);
	CollectorImmortal.prepare(immortalVM, null);
      }
      Synchronize.releaseBarrier();
    }
  }

  /**
   * Clean up after a collection. 
   */
  private void release() {
    remset.release();
    nursery.reset();   // reset the nursery allocator
    
    if (Synchronize.acquireBarrier()) {
      nurseryMR.reset(); // reset the nursery memory resource
      CollectorCopying.release(nurseryVM, nurseryMR);
      
      if (fullHeapGC) {
	CollectorLea.release(msVM, msMR);
	CollectorImmortal.release(immortalVM, null);
	fullHeapGC = false;
      }
      Synchronize.releaseBarrier();
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private AllocatorBumpPointer nursery;
  private AllocatorLea ms;
  private AllocatorBumpPointer immortal;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  // virtual memory regions
  private static VMResource nursery;
  private static VMResource msVM;
  private static VMResource immortalVM;
  private static VMResource remsetVM;

  // memory resources
  private static MemoryResource nurseryMR;
  private static MemoryResource msMR;
  private static MemoryResource immortalMR;
  private static MemoryResource remsetMR;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Final class variables (aka constants)
  //
  private static final Address NURSERY_START;
  private static final EXTENT NURSERY_VM_SIZE;
  private static final Address MARKSWEEP_START;
  private static final EXTENT MARKSWEEP_VM_SIZE;
  private static final Address REMSET_START;
  private static final EXTENT REMSET_VM_SIZE;
  private static final int NURSERY_ALLOCATOR = 0;
  private static final int MS_ALLOCATOR = 1;
  private static final int IMMORTAL_ALLOCATOR = 2;
}
   
