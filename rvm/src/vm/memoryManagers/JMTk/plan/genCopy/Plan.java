/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * All rights reserved.
 */
/**
 * This class implements the functionality of a standard
 * two-generation copying collector.  Both fixed and flexible nursery
 * sizes are supported.  If no nursery size is given on the
 * command-line then this collector behaves as an "Appel"-style
 * flexible nursery collector.  Otherwise, it behaves as a
 * conventional two-generation collector with a fixed nursery as
 * specified.<p>
 *
 * See the Jones & Lins GC book, chapter 7 for a detailed discussion
 * of generational collection and section 7.3 for an overview of the
 * flexible nursery behavior ("The Standard ML of New Jersey
 * collector"), or go to Appel's paper "Simple generational garbage
 * collection and fast allocation." SP&E 19(2):171--183, 1989.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
final class Plan implements Constants, Uninterruptible extends BasePlan {
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
    mature0VM = new VMResource(MATURE0_START, MATURE_VM_SIZE);
    mature1VM = new VMResource(MATURE1_START, MATURE_VM_SIZE);
    losVM = new VMResource(LOS_START, LOS_VM_SIZE);
    immortalVM = new VMResource(IMMORTAL_START, IMMORTAL_VM_SIZE);
    remsetVM = new VMResouce (REMSET_START, REMSET_VM_SIZE);

    // memory resources
    nurseryMR = new MemoryResource();
    matureMR = new MemoryResource();
    losMR = new MemoryResource();
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
    mature = new AllocatorBumpPointer(mature0VM, matureMR);
    los = new AllocatorLOS(losVM, losMR);
    immortal = new AllocatorBumpPointer(immortalVM, immortalMR);
    remset = new RemsetSSB(remsetVM, remsetMR);
  }

  /**
   * The boot method is called by the runtime immediately command-line
   * arguments are available.  Note that allocation must be supported
   * prior to this point because the runtime infrastructure may
   * require allocation in order to parse the command line arguments.
   * For this reason all plans should operate gracefully on the
   * default minimum heap size until the point that boot is called.
   */
  public boot() {
    super.boot();
    nurseryBlocks = Conversion.MBtoBlocks(Options.windowSize);
    if (nurseryBlocks > 0)
      variableNursery = false;
  }

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalare True if the object occupying this space will be a scalar
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public Address alloc(int allocator, EXTENT bytes, boolean isScalar,
		       AllocAdvice advice) {
    if ((allocator == NURSERY_ALLOCATOR) && (bytes.LT(LOS_SIZE_THRESHOLD)))
      return nursery.alloc(isScalar, bytes);
    else if (allocator == MATURE_ALLOCATOR)
      return mature.alloc(isScalar, bytes);
    else if (allocator == IMMORTAL_ALLOCATOR)
      return immortal.alloc(isScalar, bytes);
    else
      return los.alloc(isScalar, bytes);
  }

  /**
   * Allocate space for copying an object
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public Address allocCopy(Address original, EXTENT bytes, boolean isScalar) {
    return mature.alloc(isScalar, bytes);
  }

  /**
   * Perform a write barrier operation for this collector.
   *
   * @param srcObj The address of the object containing the pointer to
   * be written to
   * @param srcSlot The address of the pointer to be written to
   * @param tgt The address to which the source will point
   */
  public void writeBarrier(Address srcObj, Address srcSlot, Address tgt){
    // A very inefficient write barrier (assume nursery is in high memory)
    if (srcSlot.LT(NURSERY_START) && tgtSlot.GE(NURSERY_START))
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
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return The allocator number to be used for this allocation.
   */
  public int getAllocator(TypeID type, EXTENT bytes, CallSite callsite, 
			  AllocAdvice hint) {
    if (bytes.GE(LOS_SIZE_THRESHOLD))
      return LOS_ALLOCATOR;
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
   * beyond the number of blocks available, or in the case of a fixed
   * size nursery policy, when the nursery has grown to a certain
   * point. Collections are triggered through the runtime, and
   * ultimately call the <code>collect()</code> method of this class
   * or its superclass.
   */
  public void poll() {
    if (variableNursery) {
      if (getBlocksReserved() > getHeapBlocks()) {
	fullHeapGC = (nurseryMR.reservedBlocks() <= MIN_NURSERY_SIZE);
	MM.triggerCollection();
      }
    } else {
      if ((nurseryMR.reservedBlocks() > nurseryBlocks) ||
	  (getBlocksReserved() > getHeapBlocks())) {
	fullHeapGC = (nurseryMR.reservedBlocks() <= MIN_NURSERY_SIZE);
	MM.triggerCollection();
      }
    }
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
      if (fullHeapGC) {
	if (reference.GE(MATURE_START))
	  CollectorCopying.traceReference(reference);
	else if (reference.GE(LOS_START))
	  CollectorLOS.traceReference(reference);
	else if (reference.GE(IMMORTAL_START))
	  CollectorImmortal.traceReference(reference);
      } else {
	if (reference.GE(NURSERY_START))
	  CollectorCopying.traceReference(reference);
      }
    }
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
    blocks += matureMR.reservedBlocks();
    // we must account for the worst case number of blocks required
    // for copying, which equals the number of nursery and mature
    // blocks in use plus a fudge factor which accounts for
    // fragmentation
    blocks += blocks + COPY_FUDGE_BLOCKS;
    
    blocks += losMR.reservedBlocks();
    blocks += immortalMR.reservedBlocks();
    blocks += remsetMR.reservedBlocks();

    return blocks;
  }

  /**
   * Prepare for a collection.
   */
  private void prepare() {
    if (Synchronize.getBarrier()) {
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
  private AllocatorBumpPointer mature;
  private AllocatorLea los;
  private AllocatorBumpPointer immortal;
  private RemsetSSB remset;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //

  // virtual memory regions
  private static VMResource nurseryVM;
  private static VMResource mature0VM;
  private static VMResource mature1VM;
  private static VMResource losVM;
  private static VMResource immortalVM;
  private static VMResource remsetVM;

  // memory resources
  private static MemoryResource nurseryMR;
  private static MemoryResource matureMR;
  private static MemoryResource losMR;
  private static MemoryResource immortalMR;
  private static MemoryResource remsetMR;

  // other
  private static int nurseryBlocks = 0;
  private static boolean variableNursery = true;
  private static boolean fullHeapGC = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Final class variables (aka constants)
  //
  private static final Address NURSERY_START;
  private static final EXTENT NURSERY_VM_SIZE;
  private static final Address MATURE0_START;
  private static final Address MATURE1_START;
  private static final EXTENT MATURE_VM_SIZE;
  private static final Address REMSET_START;
  private static final EXTENT REMSET_VM_SIZE;
  private static final int NURSERY_ALLOCATOR = 0;
  private static final int MATURE_ALLOCATOR = 1;
  private static final int LOS_ALLOCATOR = 2;
  private static final int IMMORTAL_ALLOCATOR = 3;
}
