/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * All rights reserved.
 */
/**
 * This class implements a simple mark-sweep collector.<p>
 *
 * See the Jones & Lins GC book, chapter 4 for a detailed discussion
 * of mark-sweep collection.
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
  // Static methods and fields of MM are those with global scope, such
  // as virtual memory and memory resources.  This stands in contrast
  // to instance methods which are for fast, unsychronized access to
  // thread-local structures such as bump pointers and remsets.
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    // virtual memory resources
    msVM = new VMResource(MARKSWEEP_START, MARKSWEEP_VM_SIZE);
    immortalVM = new VMResource(IMMORTAL_START, IMMORTAL_VM_SIZE);

    // memory resources
    msMR = new MemoryResource();
    immortalMR = new MemoryResource();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  // Instances of MM map 1:1 to "kernel threads" (aka CPUs or in Jikes
  // RVM, VM_Processor).  Thus instance methods allow fast,
  // unsychronized access to MM utilities such as allocation and
  // collection.  Each instance rests on static resources (such as
  // memory and virtual memory resources) which are "global" and
  // therefore "static" members of MM.
  //

  /**
   * Constructor
   */
  Plan() {
    ms = new AllocatorLea(msVM, msMR);
    immortal = new AllocatorBumpPointer(immortalVM, immortalMR);
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
    if (allocator == IMMORTAL_ALLOCATOR)
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
    return null;  // no copying in this collector
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
    return DEFAULT_ALLOCATOR;
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
   */
  public void poll() {
    if (getReservedBlocks() > getHeapBlocks())
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
      if (reference.GE(MARKSWEEK_START))
	CollectorLea.traceReference(reference);
      else if (reference.GE(IMMORTAL_START))
	CollectorImmortal.traceReference(reference);
    } // else this is not a heap pointer
  }

  /**
   * Perform a collection (either minor or full-heap, depending on the
   * size of the nursery).
   */
  public void collect() {
    prepare();
    collect(null);
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

    blocks = msMR.reservedBlocks();
    blocks += immortalMR.reservedBlocks();
    return blocks;
  }

  /**
   * Prepare for a collection.
   */
  private void prepare() {
    if (Synchronize.getBarrier()) {
      // prepare each of the collected regions
      CollectorLea.prepare(msVM, msMR);
      CollectorImmortal.prepare(immortalVM, null);
      Synchronize.releaseBarrier();
    }
  }

  /**
   * Clean up after a collection. 
   */
  private void release() {
    remset.release();
    if (Synchronize.acquireBarrier()) {
      CollectorLea.release(msVM, msMR);
      CollectorImmortal.release(immortalVM, null);
      Synchronize.releaseBarrier();
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private AllocatorLea ms;
  private AllocatorBumpPointer immortal;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  // virtual memory regions
  private static VMResource msVM;
  private static VMResource immortalVM;

  // memory resources
  private static MemoryResource msMR;
  private static MemoryResource immortalMR;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Final class variables (aka constants)
  //
  private static final Address MARKSWEEP_START;
  private static final EXTENT MARKSWEEP_VM_SIZE;
  private static final Address IMMORTAL_START;
  private static final Address IMMORTAL_VM_SIZE;
  private static final int DEFAULT_ALLOCATOR = 0;
  private static final int IMMORTAL_ALLOCATOR = 1;
}
   
