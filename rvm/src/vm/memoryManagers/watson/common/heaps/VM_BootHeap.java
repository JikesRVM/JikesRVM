/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dave Grove
 */
final class VM_BootHeap extends VM_Heap
  implements VM_Uninterruptible {

  VM_BootHeap() {
    super("Boot Image Heap");
  }

  /**
   * Allocate size bytes of raw memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory(int size) {
    // Can't allocate anything in the bootheap!
    VM.sysFail("allocateZeroedMemory on VM_BootHeap forbidden");
    return VM_Address.zero();
  }

  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   */
  protected void postAllocationProcessing(Object newObj) { 
    // nothing to do in this heap
  }
}
