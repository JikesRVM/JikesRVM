/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An area representing space manipulated by malloc-free.
 * The main functionality is to provide the refInHeap/addrInHeap functionality.
 * <p>
 * NOTE: Space allocated from the MallocHeap is not managed by
 *       Jikes RVM's garbage collectors.  In particular, objects in
 *       this space are not traced during collection.  Therefore
 *       objects in the malloc heap <STRONG>must not</STRONG> contain
 *       pointers to objects to any copying space and also must not
 *       be the only pointer to an object in another space.
 *       We'd really like to disallow pointers from a malloc heap to 
 *       all other heaps, but in practice pointers from the malloc heap
 *       to the bootimage heap do exist and must be allowed.
 *
 *  @author Perry Cheng
 *  @author David Grove
 */
public class VM_MallocHeap extends VM_Heap 
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  // Internal management
  private VM_ProcessorLock spaceLock = new VM_ProcessorLock();

  /**
   * Initialize for boot image - called from init of various collectors
   */
  VM_MallocHeap() {
    super("Malloc Heap");
  }

  /**
   * Initialize for execution.
   */
  public void attach (int size) { VM.sysFail("Cannot attach malloc space with size"); }

  /**
   * Get total amount of memory used by malloc space.
   *
   * @return the number of bytes
   */
  public int totalMemory () {
    return size;
  }

  /**
   * Allocate a scalar object. Fills in the header for the object,
   * and set all data fields to zero. Assumes that type is already initialized.
   * Disables thread switching during allocation. 
   * 
   * @param type  VM_Class of type to be instantiated
   *
   * @return the reference for the allocated object
   */
  public Object atomicAllocateScalar(VM_Class type) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    Object o = allocateScalar(type);
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    return o;
  }

  /**
   * Allocate an array object. Fills in the header for the object,
   * sets the array length to the specified length, and sets
   * all data fields to zero.  Assumes that type is already initialized.
   * Disables thread switching during allocation. 
   *
   * @param type  VM_Array of type to be instantiated
   * @param numElements  number of array elements
   *
   * @return the reference for the allocated array object 
   */
  public Object atomicAllocateArray(VM_Array type, int numElements) {
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    Object o = allocateArray(type, numElements);
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    return o;
  }
  
  /**
   * Atomically free an array object.
   * @param obj the object to free
   */
  public void atomicFreeArray(Object o) {
    // NOTE: making an evil assumption about the object model here
    //       to avoid requiring object model to have an object-to-base-addr function.
    //       This might be the wrong design decison.
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    VM_Type t = VM_Magic.getObjectType(o);
    int fudge = VM_ObjectModel.computeArrayHeaderSize(t.asArray());
    VM_Address start = VM_Magic.objectAsAddress(o).sub(fudge);
    free(start);
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
  }

  
  /**
   * Free a memory region.
   * @param addr the pointer to free
   */
  public void free(VM_Address addr) {
    VM.sysCall1(VM_BootRecord.the_boot_record.sysFreeIP, addr.toInt());
    // Cannot correctly change start/end here
  }

  /**
   * Allocate size bytes of zeroed memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory(int size) {
    // NOTE: must use processorLock instead of synchronized virtual method
    //       because we can't give up the virtual processor.
    //       This method is sometimes called when the GC system is in a delicate state.
    spaceLock.lock();

    VM_Address region = VM_Address.fromInt(VM.sysCall1(VM_BootRecord.the_boot_record.sysMallocIP, size));
    VM_Address regionEnd = region.add(size);
    if (region.isZero()) {
      VM.sysWriteln("VM_MallocHeap failed to malloc ", size, " bytes");
      VM.sysFail("Exiting VM with fatal error");
    }
    VM_Memory.zero(region, regionEnd);

    if (start.isZero() || region.LT(start)) start = region;
    if (regionEnd.GT(end)) end = regionEnd;
    VM_Processor pr = VM_Processor.getCurrentProcessor();
    pr.disableThreadSwitching();
    setAuxiliary();
    pr.enableThreadSwitching();
    /*
      VM.sysWrite("malloc.allocate:  region = "); VM.sysWrite(region);
      VM.sysWrite("    "); show();
      VM_Scheduler.dumpStack();
    */
    spaceLock.unlock();

    return region;
  }

  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   */
  protected void postAllocationProcessing(Object newObj) { 
    // nothing to do in this heap since the GC subsystem
    // ignores objects in the malloc heap.
  }
}
