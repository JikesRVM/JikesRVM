/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *  An area representing space manipulated by malloc-free.
 *  The main functionality is to provide the refInHeap/addrInHeap functionality.
 *
 *  @author Perry Cheng
 */

public class VM_MallocHeap extends VM_Heap 
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  // Internal management
  private VM_BootRecord bootrecord;
  private int markValue;
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

  public void attach (VM_BootRecord br) { bootrecord = br; }

  /**
   * Get total amount of memory used by malloc space.
   *
   * @return the number of bytes
   */
  public int totalMemory () {
    return size;
  }

  /**
   * Mark an object in the boot heap
   * @param ref the object reference to mark
   * @return whether or not the object was already marked
   */
  public boolean mark(VM_Address ref) {
    return VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), markValue);
  }

  /**
   * Is the object reference live?
   */
  public boolean isLive(VM_Address ref) {
    Object obj = VM_Magic.addressAsObject(ref);
    return VM_AllocatorHeader.testMarkBit(obj, markValue);
  }

  /**
   * Work to do before collection starts
   */
  public void startCollect() {
    // flip the sense of the mark bit.
    markValue = markValue ^ VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
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

    VM_Address region = VM_Address.fromInt(VM.sysCall1(bootrecord.sysMallocIP, size));
    VM_Address regionEnd = region.add(size);
    if (region.isZero()) {
      VM.sysFail("VM_MallocHeap failed to malloc " + size  + " bytes");
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
    if (VM_Collector.NEEDS_WRITE_BARRIER) {
      VM_ObjectModel.initializeAvailableByte(newObj); 
      VM_AllocatorHeader.setBarrierBit(newObj);
    }    
    VM_AllocatorHeader.writeMarkBit(newObj, markValue);
  }

  void free(VM_Address addr) {
    VM.sysCall1(bootrecord.sysFreeIP, addr.toInt());
    // Cannot correctly change start/end here
  }

}
