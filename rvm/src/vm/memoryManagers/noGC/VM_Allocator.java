/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Non-collecting version of allocator.
 * <p>
 * Trivial implementation for illustrative purposes, not for production use.
 * Allocates space by moving a freespace pointer sequentially from the 
 * beginning of the heap to the end.  Ignores requests for garbage collection.
 * Produces an OutOfMemeryError message when it reaches the end of the heap.
 *
 * @author Derek Lieber
 */
public class VM_Allocator implements VM_Constants {
  
  private static int    startAddress; // start of heap
  private static int    freeAddress;  // start of free area within heap
  private static int    endAddress;   // end of heap
  private static Object lock;         // lock for serializing heap accesses
  
  /**
   * Initialize for boot image.
   */
  static void init() {
    lock = new Object();
    VM_GCWorkQueue.init();
    VM_CollectorThread.init();
  }
  
  /**
   * Initialize for execution.
   *
   * @param bootrecord  reference for the system VM_BootRecord
   */
  static void
    boot(VM_BootRecord bootrecord)
  {
    startAddress = freeAddress = bootrecord.freeAddress;
    endAddress   = bootrecord.endAddress;

    // if collection wants to use the utility methods of VM_GCUtil, ex. scanStack,
    // scanStatics, scanObjectOrArray, etc. then its boot method must by called.
    //
    VM_GCUtil.boot();

    VM_Finalizer.setup();   // will allocate a lock object for finalizer lists
    
    // touch memory pages now (instead of during individual allocations, later)
    //
    VM_Memory.zero(freeAddress, endAddress);
  }
  
  /**
   * Request a garbage collection cycle.
   */
  public static void
    gc() {
    
    // Tell gc thread to reclaim space, then wait for it to complete its work.
    // The gc thread will do its work by calling gc_collect(), below.
    //
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }

  /**
   * return true if a garbage collection is in progress
   */
  public static boolean
  gcInProgress() {
    return false;
  }

  /**
   * Get total memory available, in bytes.
   *
   * @return the number of bytes
   */
  public static long
    totalMemory() {
    return endAddress - startAddress;
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   *
   * @return number of bytes available
   */
  public static long
    freeMemory() {
    return endAddress - freeAddress;
  }
  
  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero.
   *
   * @param size         size of object (including header), in bytes
   * @param tib          type information block for object
   * @param hasFinalizer hasFinalizer flag
   *
   * @return the reference for the allocated object
   */
  public static Object
    allocateScalar (int size, Object[] tib, boolean hasFinalizer)
    throws OutOfMemoryError {
    
    if (VM.VerifyAssertions && VM_Thread.getCurrentThread().disallowAllocationsByThisThread)
      VM.sysFail("VM_Allocator: allocation requested while gc disabled");
    
    // allocate (zero-filled) space
    //
    //  |<--------------------size---------------->|
    //  .                            |<--hdr size->|
    //  .                            |<--- hdr offset--->|
    //  +-------------------+--------+------+------+-----+-----+
    //  |         ...       | field0 | tib  |status| free| free|
    //  +-------------------+--------+------+------+-----+-----+
    //                                   freeAddress^     ^objAddress
    
    int objAddress;
    synchronized (lock)
      {
	VM_Allocator.freeAddress += size;
	if (VM_Allocator.freeAddress > VM_Allocator.endAddress)
	  VM.sysFail("VM_Allocator: OutOfMemoryError");
	objAddress = VM_Allocator.freeAddress - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;
      }
    
    // set .tib field
    //
    VM_Magic.setMemoryWord(objAddress + OBJECT_TIB_OFFSET, VM_Magic.objectAsAddress(tib));

    // store address of new object into a field of type Object, to keep it
    // live in case GC occurs during call to addElement
    Object objRef = VM_Magic.addressAsObject(objAddress);
    
    // if the class has a finalizer, add object to list of
    // live objects with finalizers
    //
    if( hasFinalizer ) {
      VM_Finalizer.addElement(objRef);
    }

    // return object reference
    //
    return objRef;
    
  }
  
  /**
   * Allocate a scalar object & optionally clone another object.
   * Fills in the header for the object.  If a clone is specified,
   * then the data fields of the clone are copied into the new
   * object.  Otherwise, the data fields are set to 0.
   *
   * @param size     size of object (including header), in bytes
   * @param tib      type information block for object
   * @param cloneSrc object from which to copy field values
   *                 (null --> set all fields to 0/null)
   *
   * @return the reference for the allocated object
   */
  public static Object
    cloneScalar (int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {

    if (VM.VerifyAssertions && VM_Thread.getCurrentThread().disallowAllocationsByThisThread)
      VM.sysFail("VM_Allocator: allocation requested while gc disabled");
    
    // allocate (zero-filled) space
    //
    //  |<--------------------size---------------->|
    //  .                            |<--hdr size->|
    //  .                            |<--- hdr offset--->|
    //  +-------------------+--------+------+------+-----+-----+
    //  |         ...       | field0 | tib  |status| free| free|
    //  +-------------------+--------+------+------+-----+-----+
    //                                   freeAddress^     ^objAddress

    boolean hasFinalizer =
      VM_Magic.addressAsType(VM_Magic.getMemoryWord(VM_Magic.objectAsAddress(tib))).hasFinalizer();
    
    int objAddress;
    synchronized (lock)
      {
	VM_Allocator.freeAddress += size;
	if (VM_Allocator.freeAddress > VM_Allocator.endAddress)
	  VM.sysFail("VM_Allocator: OutOfMemoryError");
	objAddress = VM_Allocator.freeAddress - SCALAR_HEADER_SIZE - OBJECT_HEADER_OFFSET;
      }
    
    // set .tib field
    //
    VM_Magic.setMemoryWord(objAddress + OBJECT_TIB_OFFSET, VM_Magic.objectAsAddress(tib));
    
    // initialize fields
    //
    if (VM.VerifyAssertions) VM.assert(cloneSrc != null);
    int cnt = size - SCALAR_HEADER_SIZE;
    int src = VM_Magic.objectAsAddress(cloneSrc) + OBJECT_HEADER_OFFSET - cnt;
    int dst = objAddress                         + OBJECT_HEADER_OFFSET - cnt;
    VM_Memory.aligned32Copy(dst, src, cnt);

    // store address of new object into a field of type Object, to keep it
    // live in case GC occurs during call to addElement
    Object objRef = VM_Magic.addressAsObject(objAddress);
    
    // if the class has a finalizer, add object to list of
    // live objects with finalizers
    //
    if( hasFinalizer )
      VM_Finalizer.addElement(objRef);
    
    // return object reference
    //
    return objRef;
  }
  
  /**
   * Allocate an array object. Fills in the header for the object,
   * sets the array length to the specified length, and sets
   * all data fields to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   *
   * @return the reference for the allocated array object 
   */
  public static Object
    allocateArray (int numElements, int size, Object[] tib)
    throws OutOfMemoryError {
    
    if (VM.VerifyAssertions && VM_Thread.getCurrentThread().disallowAllocationsByThisThread)
      VM.sysFail("VM_Allocator: allocation requested while gc disabled");
    
    // allocate (zero-filled) space
    //
    //  |<--------------------size---------------->|
    //  |<-----hdr size---->|                      .
    //  |<-----hdr offset-->|                      .
    //  +------+------+-----+------+---------------+----+
    //  | tib  |status| len | elt0 |     ...       |free|
    //  +------+------+-----+------+---------------+----+
    //                        ^objAddress           ^freeAddress
    
    // array size might not be a word multiple,
    // so we must round up "freeAddress" to word multiple in order
    // to preserve alignment for future allocations.
    //
    int objAddress;
    synchronized (lock)
      {
	objAddress = VM_Allocator.freeAddress - OBJECT_HEADER_OFFSET;
	VM_Allocator.freeAddress += ((size + 3) & ~3);
	if (VM_Allocator.freeAddress > VM_Allocator.endAddress)
	  VM.sysFail("VM_Allocator: OutOfMemoryError");
      }
    
    // set .tib field
    //
    VM_Magic.setMemoryWord(objAddress + OBJECT_TIB_OFFSET, VM_Magic.objectAsAddress(tib));
    
    // set .length field
    //
    VM_Magic.setMemoryWord(objAddress + ARRAY_LENGTH_OFFSET, numElements);
    
    // return object reference
    //
    return VM_Magic.addressAsObject(objAddress);
  }
  
  /**
   * Allocate an array object and optionally clone another array.
   * Fills in the header for the object and sets the array length
   * to the specified length.  If an object to clone is specified,
   * then the data elements of the clone are copied into the new
   * array.  Otherwise, the elements are set to zero.
   *
   * @param numElements  number of array elements
   * @param size         size of array object (including header), in bytes
   * @param tib          type information block for array object
   * @param cloneSrc     object from which to copy field values
   *                     (null --> set all fields to 0/null)
   *
   * @return the reference for the allocated array object 
   */
  public static Object
    cloneArray (int numElements, int size, Object[] tib, Object cloneSrc)
    throws OutOfMemoryError {
    
    if (VM.VerifyAssertions && VM_Thread.getCurrentThread().disallowAllocationsByThisThread)
      VM.sysFail("VM_Allocator: allocation requested while gc disabled");
    
    // allocate (zero-filled) space
    //
    //  |<--------------------size---------------->|
    //  |<-----hdr size---->|                      .
    //  |<-----hdr offset-->|                      .
    //  +------+------+-----+------+---------------+----+
    //  | tib  |status| len | elt0 |     ...       |free|
    //  +------+------+-----+------+---------------+----+
    //                        ^objAddress           ^freeAddress
    
    // array size might not be a word multiple,
    // so we must round up "freeAddress" to word multiple in order
    // to preserve alignment for future allocations.
    //
    int objAddress;
    synchronized (lock)
      {
	objAddress = VM_Allocator.freeAddress - OBJECT_HEADER_OFFSET;
	VM_Allocator.freeAddress += ((size + 3) & ~3);
	if (VM_Allocator.freeAddress > VM_Allocator.endAddress)
	  VM.sysFail("VM_Allocator: OutOfMemoryError");
      }
    
    // set .tib field
    //
    VM_Magic.setMemoryWord(objAddress + OBJECT_TIB_OFFSET, VM_Magic.objectAsAddress(tib));
    
    // set .length field
    //
    VM_Magic.setMemoryWord(objAddress + ARRAY_LENGTH_OFFSET, numElements);
    
    // initialize array elements
    //
    if (VM.VerifyAssertions) VM.assert(cloneSrc != null);
    int cnt = size - ARRAY_HEADER_SIZE;
    int src = VM_Magic.objectAsAddress(cloneSrc);
    int dst = objAddress;
    VM_Memory.aligned32Copy(dst, src, cnt); 
    
    // return object reference
    //
    return VM_Magic.addressAsObject(objAddress);
  }
  
  /**
   * Reclaim unreferenced memory (ignored)
   */
  static void
    gc_collect()  {
    // VM.sysWrite("VM_Allocator: gc unimplemented: nothing reclaimed\n");
  }
  
  // methods called from utility methods of VM_GCUtil, VM_ScanObject,
  // VM_ScanStack, VM_ScanStatics
  //
  static final void    processPtrField( int location ) {}
  static final int     processPtrValue( int reference ) { return 0; }

  // Other fields and methods referenced from common GC classes or elsewhere
  // in VM (ex. VM_Entrypoints)
  //
  static final int     BEING_FORWARDED_PATTERN = 0;
  static final int     MARK_VALUE = 0;
  static final boolean movesObjects = false;
  static final boolean writeBarrier = false;
  static boolean       gcInProgress;
  static int           areaCurrentAddress;
  static int           matureCurrentAddress;
  static void          gcSetup(int numProcessors) {}
  static void          setupProcessor(VM_Processor p) {}
  static boolean       processFinalizerListElement (VM_FinalizerListElement le) { return true; }
  static void          processWriteBufferEntry (int wbref) {}
}
