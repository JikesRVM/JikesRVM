/*
 * (C) Copyright IBM Corp. 2002
 */

/**
 * Dynamically allocate objects that live forever, using a simple pointer-bumping technique.
 *
 * @author David F. Bacon
 * @author Perry Cheng
 * 
 */

final class VM_ImmortalHeap
    extends VM_Heap
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{

    private VM_Address allocationCursor;

    private VM_ProcessorLock lock = new VM_ProcessorLock();

    /**
     * Initialize for boot image - called from init of various collectors or spaces
     */
    VM_ImmortalHeap() {
	super("Immortal Heap");
	lock = new VM_ProcessorLock();      
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
     * Allocate a chunk of memory of a given size.  Always inlined.
     *   @param size Number of bytes to allocate
     *   @return Address of allocated storage
     */
    VM_Address allocateRawMemory (int size) {
	VM_Magic.pragmaInline();
	lock.lock();

	VM_Address result = internalAllocate(size);

	lock.unlock();
	return result;
    }


    /**
     * Allocate a chunk of memory of a given size.  Always inlined.
     *   @param size Number of bytes to allocate
     *   @param alignment Alignment specifier; must be a power of two
     *   @return Address of allocated storage
     */
    VM_Address allocateRawMemory (int size, int alignment) {
	VM_Magic.pragmaInline();
	return allocateRawMemory(size, alignment, 0);
    }



    /**
     * Allocate a chunk of memory of a given size.  Always inlined.
     *   @param size Number of bytes to allocate
     *   @param alignment Alignment specifier; must be a power of two
     *   @param offset Offset within the object that must be aligned
     *   @return Address of allocated storage
     */
    VM_Address allocateRawMemory (int size, int alignment, int offset) {
	VM_Magic.pragmaInline();
	lock.lock();
	
	// reserve space for offset bytes
	allocationCursor = allocationCursor.add(offset);
	// align the interior portion of the requested space
	allocationCursor = VM_Memory.align(allocationCursor, alignment);
	// allocate remaining space and 
	VM_Address result = internalAllocate(size - offset).sub(offset);

	lock.unlock();
	return result;
    }



    /**
     * Allocate a chunk of memory of a given size.  Always inlined.
     * Internal version does no locking.
     *   @param size Number of bytes to allocate
     *   @return Address of allocated storage
     */
    private VM_Address internalAllocate (int size) {
	VM_Magic.pragmaInline();
	
	VM_Address result = allocationCursor;
	allocationCursor = allocationCursor.add(size);
	if (allocationCursor.GT(end))
	    VM.sysFail("Immortal heap space exhausted");
	return result;
    }

    private static short[] dummyShortArray = new short[1];

    public short[] allocateShortArray(int numElements) {
	Object [] tib = VM_ObjectModel.getTIB(dummyShortArray);
	// XXXXX Is this the right way to compute size in object model?
	int size = ((2 * numElements + 3) & ~3) + VM_ObjectModel.computeHeaderSize(tib);
	VM_Address region = allocateRawMemory(size);
	return (short[]) VM_ObjectModel.initializeArray(region, tib,
							numElements, size);
    }
}
