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
  
  private static VM_ImmortalSpace     immortalSpace;
  private static VM_Synchronizer lock;         // lock for serializing heap accesses

  static int verbose = 0; // control chattering during progress of GC
  
  /**
   * Initialize for boot image.
   */
  static void init() {
    lock = new VM_Synchronizer();
    immortalSpace = new VM_ImmortalSpace();
    VM_GCWorkQueue.init();
    VM_CollectorThread.init();
  }
  
  /**
   * Initialize for execution.
   *
   * @param bootrecord  reference for the system VM_BootRecord
   */
  static void boot(VM_BootRecord bootrecord) {

    immortalSpace.attach(bootrecord.smallSpaceSize);

    // if collection wants to use the utility methods of VM_GCUtil, ex. scanStack,
    // scanStatics, scanObjectOrArray, etc. then its boot method must by called.
    //
    VM_GCUtil.boot();

    VM_Finalizer.setup();   // will allocate a lock object for finalizer lists
    
    // touch memory pages now (instead of during individual allocations, later)
    //
    VM_Memory.zero(immortalSpace.start, immortalSpace.end);
  }
  
  /**
   * Request a garbage collection cycle.
   */
  public static void gc() {
    // Tell gc thread to reclaim space, then wait for it to complete its work.
    // The gc thread will do its work by calling collect(), below.
    //
    VM_CollectorThread.collect(VM_CollectorThread.collect);
  }

  /**
   * return true if a garbage collection is in progress
   */
  public static boolean gcInProgress() {
    return false;
  }

  /**
   * Get total memory available, in bytes.
   *
   * @return the number of bytes
   */
  public static long totalMemory() {
    return immortalSpace.size;
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   *
   * @return number of bytes available
   */
  public static long freeMemory() {
    return immortalSpace.available();
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
  public static Object allocateScalar (int size, Object[] tib, boolean hasFinalizer) throws OutOfMemoryError {
    VM_Address objAddress = getHeapSpace(size);
    Object objRef = VM_ObjectModel.initializeScalar(objAddress, tib, size);
    if (hasFinalizer) VM_Finalizer.addElement(objRef);
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
  public static Object cloneScalar (int size, Object[] tib, Object cloneSrc) throws OutOfMemoryError {
    boolean hasFinalizer =
      VM_Magic.addressAsType(VM_Magic.getMemoryAddress(VM_Magic.objectAsAddress(tib))).hasFinalizer();
    VM_Address objAddress = getHeapSpace(size);
    Object objRef = VM_ObjectModel.initializeScalar(objAddress, tib, size);

    // initialize object fields with data from passed in object to clone
    if (cloneSrc != null) {
      VM_ObjectModel.initializeScalarClone(objRef, cloneSrc, size);
    }
    
    if (hasFinalizer)  VM_Finalizer.addElement(objRef);

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
  public static Object allocateArray (int numElements, int size, Object[] tib) throws OutOfMemoryError {
    size = (size + 3) & ~3; // preserve word alignment
    VM_Address objAddress = getHeapSpace(size);
    return VM_ObjectModel.initializeArray(objAddress, tib, numElements, size);
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
  public static Object cloneArray (int numElements, int size, Object[] tib, Object cloneSrc) throws OutOfMemoryError {
    size = (size + 3) & ~3; // preserve word alignment
    VM_Address objAddress = getHeapSpace(size);
    Object objRef = VM_ObjectModel.initializeArray(objAddress, tib, numElements, size);

     // initialize array elements
     if (cloneSrc != null) {
       VM_ObjectModel.initializeArrayClone(objRef, cloneSrc, size);
     }

     return objRef;  // return reference for allocated array
  }


  /**
   * Allocate size bytes of heap space.
   */
  private static VM_Address getHeapSpace(int size) {
    
    VM_Address region;
    size = ((size + 3) & ~3);
    synchronized (lock) {
	region = immortalSpace.allocate(size);
    }
    return region;
  }
  
  /**
   * Reclaim unreferenced memory (ignored)
   */
  static void collect()  {
    // VM.sysWrite("VM_Allocator: gc unimplemented: nothing reclaimed\n");
  }

  // methods called from utility methods of VM_GCUtil, VM_ScanObject,
  // VM_ScanStack, VM_ScanStatics
  //
  static final void       processPtrField( VM_Address location ) { VM.assert(false); }
  static final VM_Address processPtrValue( VM_Address reference ) { VM.assert(false); return null; }
  static final void       processWriteBufferEntry( VM_Address discard ) { VM.assert(false); }

  // Other fields and methods referenced from common GC classes or elsewhere
  // in VM (ex. VM_Entrypoints)
  //
  static final int     MARK_VALUE = 0;
  static final boolean movesObjects = false;
  static final boolean writeBarrier = false;
  static boolean       gcInProgress;
  static VM_Address    areaCurrentAddress;
  static VM_Address    matureCurrentAddress;
  static void          gcSetup(int numProcessors) {}
  static void          setupProcessor(VM_Processor p) {}
  static boolean       processFinalizerListElement (VM_FinalizerListElement le) { return true; }
  static void          processWriteBufferEntry (int wbref) {}

  static int gcCount = 0;
}
