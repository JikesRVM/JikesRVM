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
   * Allocate size bytes of zeroed memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected synchronized VM_Address allocateZeroedMemory(int size) {
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
  }

  void free(VM_Address addr) {
    VM.sysCall1(bootrecord.sysFreeIP, addr.toInt());
    // Cannot correctly change start/end here
  }

}
