/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * Dynamically allocate objects that live forever, using a simple pointer-bumping technique.
 *
 * @author David F. Bacon
 * @author Perry Cheng
 */
final class VM_ImmortalHeap extends VM_Heap
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible, VM_AllocatorHeaderConstants {

  private VM_Address allocationCursor;
  private int markValue;
  private VM_ProcessorLock spaceLock = new VM_ProcessorLock();

  /**
   * A side mark vector, in case object model doesn't have mark bit in object
   */
  private VM_SideMarkVector markVector;

  /**
   * Initialize for boot image - called from init of various collectors or spaces
   */
  VM_ImmortalHeap() {
    super("Immortal Heap");
    if (USE_SIDE_MARK_VECTOR) {
      markVector = new VM_SideMarkVector();
    }
  }

  /**
   * Initialize for execution.
   */
  public void attach (int size) {
    super.attach(size);
    allocationCursor = start;
  }

  void setAuxiliary() {
    super.setAuxiliary();
    if (USE_SIDE_MARK_VECTOR) {
      markVector.boot(mallocHeap, start, end);
    }
  }

  /**
   * Get total amount of memory used by immortal space.
   *
   * @return the number of bytes
   */
  public int totalMemory () {
    return size;
  }

  /**
   * Get the total amount of memory available in immortal space.
   * @return the number of bytes available
   */
  public int freeMemory() {
    return end.diff(allocationCursor);
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


  /**
   * Allocate an array object whose pointer is N bit aligned
   * 
   * @param type  VM_Array of type to be instantiated
   * @param numElements  number of array elements
   * @param alignment 
   *
   * @return the reference for the allocated array object 
   */
  public Object allocateAlignedArray(VM_Array type, int numElements, int alignment) {
    if (VM.VerifyAssertions) VM.assert(type.isInitialized());
    int size = type.getInstanceSize(numElements);
    size = VM_Memory.align(size, WORDSIZE);
    Object[] tib = type.getTypeInformationBlock();
    int offset = VM_JavaHeader.computeArrayHeaderSize(type);
    VM_Address region = allocateZeroedMemory(size, alignment, offset);
    VM_GCStatistics.profileAlloc(region, size, tib); // profile/debug: usually inlined away to nothing
    Object newObj = VM_ObjectModel.initializeArray(region, tib, numElements, size);
    postAllocationProcessing(newObj);
    return newObj;
  }

  
  /**
   * Allocate a chunk of memory of a given size.
   *   @param size Number of bytes to allocate
   *   @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory (int size) {
    return allocateZeroedMemory(size, 1, 0);
  }

  /**
   * Allocate a chunk of memory of a given size.
   *   @param size Number of bytes to allocate
   *   @param alignment Alignment specifier; must be a power of two
   *   @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory (int size, int alignment) {
    return allocateZeroedMemory(size, alignment, 0);
  }

  /**
   * Allocate a chunk of memory of a given size.
   *   @param size Number of bytes to allocate
   *   @param alignment Alignment specifier; must be a power of two
   *   @param offset Offset within the object that must be aligned
   *   @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory (int size, int alignment, int offset) {
    VM_Address region = allocateInternal(size, alignment, offset);
    VM_Memory.zeroTemp(region, size);
    return region;
  }

  private VM_Address allocateInternal (int size, int alignment, int offset) {
    // NOTE: must use processorLock instead of synchronized virtual method
    //       because we can't give up the virtual processor.
    //       This method is sometimes called when the GC system is in a delicate state.
    spaceLock.lock();

    // reserve space for offset bytes
    allocationCursor = allocationCursor.add(offset);
    // align the interior portion of the requested space
    allocationCursor = VM_Memory.align(allocationCursor, alignment);
    VM_Address result = allocationCursor;
    // allocate remaining space 
    allocationCursor = allocationCursor.add(size - offset);
    if (allocationCursor.GT(end))
      VM.sysFail("Immortal heap space exhausted");
    // subtract back offset bytes

    spaceLock.unlock();

    return result.sub(offset);
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
    if (USE_SIDE_MARK_VECTOR) {
      markVector.writeMarkBit(newObj, markValue);
    } else {
      VM_AllocatorHeader.writeMarkBit(newObj, markValue);
    }
  }
}
