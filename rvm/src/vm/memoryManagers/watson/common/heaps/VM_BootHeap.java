/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Dave Grove
 */
final class VM_BootHeap extends VM_Heap
  implements VM_Uninterruptible,
	     VM_AllocatorHeaderConstants {

  VM_BootHeap() {
    super("Boot Image Heap");
    if (USE_SIDE_MARK_VECTOR) {
      markVector = new VM_SideMarkVector();
    }
  }

  /**
   * the current mark value
   */
  private int markValue;

  /**
   * A side mark vector, in case object model doesn't have mark bit in object
   */
  private VM_SideMarkVector markVector;

  void setAuxiliary() {
    super.setAuxiliary();
    if (USE_SIDE_MARK_VECTOR) {
      markVector.boot(mallocHeap, start, end);
    }
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


  /**
   * Mark an object in the boot heap
   * @param ref the object reference to mark
   * @return whether or not the object was already marked
   */
  public boolean mark(VM_Address ref) {
    if (USE_SIDE_MARK_VECTOR) {
      return markVector.testAndMark(ref, markValue);
    } else {
      return VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), markValue);
    }
  }

  /**
   * Is the object reference live?
   */
  public boolean isLive(VM_Address ref) {
    Object obj = VM_Magic.addressAsObject(ref);
    if (USE_SIDE_MARK_VECTOR) {
      return markVector.testMarkBit(obj, markValue);
    } else {
      return VM_AllocatorHeader.testMarkBit(obj, markValue);
    }
  }

  /**
   * Work to do before collection starts
   */
  public void startCollect() {
    // flip the sense of the mark bit.
    markValue = markValue ^ VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
  }    
}
