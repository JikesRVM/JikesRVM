/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 * All rights reserved.
 */
/**
 * This class implements a <i>simple</i> reference counting collector
 * using a variation on the Deutsch-Bobrow defered reference counting
 * algorithm that uses "mutation buffers" a la Bacon et al.<p>
 *
 * <i>Note: this implementation is not concurrent, and is does not
 * collect cycles.</i> It is "stop-the-world", scavanging each time
 * the size of the mutation buffers reach some threshold.<p>
 *
 * See the Jones & Lins GC book, chapter 3 for a detailed discussion
 * of reference couting, and section 3.2 for a description of the
 * Deutsch-Bobrow algorithm.  For details on the use of "mutation
 * buffers", see the PLDI paper by Bacon et al: "Java without coffee
 * breaks: A Nonintrusive Multiprocessor Garbage Collector",
 * Proceedings of teh SIGPLAN Conference on Programming Language
 * Design and Implementation (PLDI 01) (Snowbird, Utah, June 2001),
 * ACM SIGPLAN Notices 36(5).
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
    rcVM = new VMResource(REF_COUNT_START, REF_COUNT_VM_SIZE);
    immortalVM = new VMResource(IMMORTAL_START, IMMORTAL_VM_SIZE);
    mutationBufVM = new VMResouce(MUTATION_BUF_START, MUTATION_BUF_VM_SIZE);

    // memory resources
    rcMR = new MemoryResource();
    immortalMR = new MemoryResource();
    mutationBufMR = new MemoryResource();
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
    rc = new AllocatorLea(rcVM, rcMR);
    immortal = new AllocatorBumpPointer(immortalVM, immortalMR);
    mutationBuf = new MutationBuffer(mutationBufVM, mutationBufMR);
  }

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param size The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public Address alloc(int allocator, EXTENT size, boolean isScalar,
		       AllocAdvice advice) {
    if (allocator == IMMORTAL_ALLOCATOR)
      return immortal.alloc(isScalar, size);
    else
      return rc.alloc(isScalar, size);
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param size The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public Address allocCopy(Address original, EXTENT size, boolean isScalar) {
    return null;  // no copying in this collector
  }

  /**
   * Advise the compiler/runtime which allocator to use for a
   * particular allocation.  This should be called at compile time and
   * the returned value then used for the given site at runtime.
   *
   * @param type The type id of the type being allocated
   * @param size The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @return The allocator number to be used for this allocation.
   */
  public int getAllocator(TypeID type, EXTENT size, CallSite callsite) {
    return DEFAULT_ALLOCATOR;
  }

  /**
   * Give the compiler/runtime statically generated alloction advice
   * which will be passed to the allocation routine at runtime.
   *
   * @param type The type id of the type being allocated
   * @param size The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @return Allocation advice to be passed to the allocation routine
   * at runtime
   */
  public AllocAdvice getAllocAdvice(TypeID type, EXTENT size, 
				    CallSite callsite) { return null; }

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
    if ((mutationBufMR.reservedBlocks() > MUTATION_BUFFER_SIZE_THRESHOLD)
	(getReservedBlocks() > getHeapBlocks()))
      MM.triggerCollection();
  }
  
  /**
   * Decrement a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>decrementReference</code> method.
   *
   * @param reference The reference to be decrement.
   */
  public void decrementReference(Address reference) {
    if (reference.LE(HEAP_END)) {
      if (reference.GE(REF_COUNT_START))
	CollectorLea.decrementReference(reference);
      else if (reference.GE(IMMORTAL_START))
	CollectorImmortal.decrementReference(reference);
    } // else this is not a heap pointer
  }

  /**
   * Increment a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>incrementReference</code> method.
   *
   * @param reference The reference to be decrement.
   */
  public void incrementReference(Address reference) {
    if (reference.LE(HEAP_END)) {
      if (reference.GE(REF_COUNT_START))
	CollectorLea.incrementReference(reference);
      else if (reference.GE(IMMORTAL_START))
	CollectorImmortal.incrementReference(reference);
    } // else this is not a heap pointer
  }

  /**
   * Perform a collection (scavange any zero-count objects).
   */
  public void collect() {
    prepare();
    collect(mutationBuffer);
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

    blocks = rcMR.reservedBlocks();
    blocks += immortalMR.reservedBlocks();
    return blocks;
  }

  /**
   * Prepare for a collection.
   */
  private void prepare() {
    if (Synchronize.getBarrier()) {
      // prepare each of the collected regions
      CollectorLea.prepare(rcVM, rcMR);
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
      CollectorLea.release(rcVM, rcMR);
      CollectorImmortal.release(immortalVM, null);
      Synchronize.releaseBarrier();
    }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private AllocatorLea rc;
  private AllocatorBumpPointer immortal;
  private MutationBuffer mutationBuf;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  // virtual memory regions
  private static VMResource rcVM;
  private static VMResource immortalVM;
  private static VMResource mutationBufVM;

  // memory resources
  private static MemoryResource rcMR;
  private static MemoryResource immortalMR;
  private static MemoryResource mutationBufMR;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Final class variables (aka constants)
  //
  private static final Address REF_COUNT_START;
  private static final EXTENT REF_COUNT_VM_SIZE;
  private static final Address IMMORTAL_START;
  private static final EXTENT IMMORTAL_VM_SIZE;
  private static final int DEFAULT_ALLOCATOR = 0;
  private static final int IMMORTAL_ALLOCATOR = 1;
}
   
