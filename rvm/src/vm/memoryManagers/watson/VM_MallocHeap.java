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
  private VM_ProcessorLock spaceLock;        // serializes access to large space
  private VM_BootRecord bootrecord;

  /**
   * Initialize for boot image - called from init of various collectors
   */
  VM_MallocHeap() {
    super("Malloc Heap");
    spaceLock = new VM_ProcessorLock();      // serializes access to malloc space
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

  // Allocate space from the malloc space
  //
  // param   size in bytes needed for the large object
  // return  address of first byte of the region allocated or 0 if not enough space
  //
  VM_Address allocate (int size) {

      spaceLock.lock();

      VM_Address region = VM_Address.fromInt(VM.sysCall1(bootrecord.sysMallocIP, size));
      VM_Address regionEnd = region.add(size);
      if (region.isZero()) {
	  spaceLock.release();
	  VM.sysWriteln("VM_MallocHeap failed to malloc ", size, " bytes");
	  VM.assert(false);
      }
      VM_Memory.zero(region, regionEnd);

      if (start.isZero() || region.LT(start)) start = region;
      if (regionEnd.GT(end)) end = regionEnd;
      VM_Processor pr = VM_Processor.getCurrentProcessor();
      pr.disableThreadSwitching();
      setAuxiliary();
      pr.enableThreadSwitching();
      spaceLock.release();
      /*
	VM.sysWrite("malloc.allocate:  region = "); VM.sysWrite(region);
	VM.sysWrite("    "); show();
	VM_Scheduler.dumpStack();
      */
      return region;
  }

  void free(VM_Address addr) {
      VM.sysCall1(bootrecord.sysFreeIP, addr.toInt());
      // Cannot correctly change start/end here
  }

} // VM_MallocHeap
