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
  implements VM_Constants, VM_GCConstants, VM_Uninterruptible {

  private VM_Address allocationCursor;

  /**
   * Initialize for boot image - called from init of various collectors or spaces
   */
  VM_ImmortalHeap() {
    super("Immortal Heap");
  }

  /**
   * Initialize for execution.
   */
  public void attach (int size) {
    super.attach(size);
    allocationCursor = start;
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

  private synchronized VM_Address allocateInternal (int size, int alignment, int offset) {
    VM_Magic.pragmaInline();

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
  }


  private static short[] dummyShortArray = new short[1];

  public short[] allocateShortArray(int numElements) {
    Object [] tib = VM_ObjectModel.getTIB(dummyShortArray);
    // XXXXX Is this the right way to compute size in object model?
    int size = ((2 * numElements + 3) & ~3) + VM_ObjectModel.computeHeaderSize(tib);
    VM_Address region = allocateZeroedMemory(size);
    return (short[]) VM_ObjectModel.initializeArray(region, tib,
						    numElements, size);
  }
}
