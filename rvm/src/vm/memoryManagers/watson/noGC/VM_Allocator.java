/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Trivial implementation for illustrative purposes, not for production use.
 * Allocates all objects out of the immortal heap and exits with an OutOfMemoryError
 * when the immortal heap is exhausted. 
 * <p>
 * Ignores requests for garbage collection.
 * <p>
 * Useful for the initial port to a new architecture, otherwise fairly pointless.
 * <p>
 * @author Derek Lieber
 */
public class VM_Allocator implements VM_Constants {
  private static final VM_Heap bootHeap = new VM_Heap("Boot Image Heap");   
  private static final VM_ImmortalHeap immortalHeap = new VM_ImmortalHeap();

  static int verbose = 0; // control chattering during progress of GC
  
  /**
   * Initialize for boot image.
   */
  static void init() {
    VM_GCWorkQueue.init();
    VM_CollectorThread.init();
  }
  
  /**
   * Initialize for execution.
   *
   * @param bootrecord  reference for the system VM_BootRecord
   */
  static void boot(VM_BootRecord bootrecord) {
    verbose = bootrecord.verboseGC;

    // attatch heaps.
    if (verbose >= 2) VM.sysWriteln("Attaching heaps");
    VM_Heap.boot(bootHeap, bootrecord);
    immortalHeap.attach(bootrecord.smallSpaceSize);

    VM_GCUtil.boot();
    VM_Finalizer.setup();
    
    // touch memory pages now (instead of during individual allocations, later)
    //
    VM_Memory.zero(immortalHeap.start, immortalHeap.end);
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
    return immortalHeap.size;
  }
  
  /**
   * Get the number of bytes currently available for object allocation.
   *
   * @return number of bytes available
   */
  public static long freeMemory() {
    return immortalHeap.freeMemory();
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
    VM_Address objAddress = immortalHeap.allocateRawMemory(size);
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
    Object objRef = allocateScalar(size, tib, hasFinalizer);
    VM_ObjectModel.initializeScalarClone(objRef, cloneSrc, size);
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
    VM_Address objAddress = immortalHeap.allocateRawMemory(size);
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
    Object objRef = allocateArray(numElements, size, tib);
    VM_ObjectModel.initializeArrayClone(objRef, cloneSrc, size);
    return objRef; 
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
