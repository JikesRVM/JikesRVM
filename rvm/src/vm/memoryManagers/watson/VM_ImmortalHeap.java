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
    implements VM_Constants, VM_GCConstants, VM_Uninterruptible
{
    int baseAddress;
    int highAddress;
    int minReference;
    int maxReference;
    int size;


    int allocationCursor;

    VM_ProcessorLock lock = new VM_ProcessorLock();


    void boot (int baseAddress, int size) {
	this.baseAddress      = baseAddress;
	this.size             = size;
	this.highAddress      = baseAddress + size;
	this.allocationCursor = baseAddress;
	this.minReference     = VM_ObjectModel.minimumObjectRef(baseAddress);
	this.maxReference     = VM_ObjectModel.maximumObjectRef(highAddress);
    }


    boolean contains (Object object) {
	VM_Magic.pragmaInline();
	return containsReference(VM_Magic.objectAsAddress(object));
    }


    boolean containsReference (int reference) {
	VM_Magic.pragmaInline();
	return (minReference <= reference) && (reference <= maxReference);
    }


    boolean containsAddress (int address) {
	VM_Magic.pragmaInline();
	return (baseAddress <= address) && (address < highAddress);
    }


    /**
     * Allocate a chunk of memory of a given size.  Always inlined.
     *   @param size Number of bytes to allocate
     *   @return Address of allocated storage
     */
    int allocateRawMemory (int size) {
	VM_Magic.pragmaInline();
	lock.lock();

	int result = internalAllocate(size);

	lock.unlock();
	return result;
    }



    /**
     * Allocate a chunk of memory of a given size.  Always inlined.
     *   @param size Number of bytes to allocate
     *   @param alignment Alignment specifier; must be a power of two
     *   @return Address of allocated storage
     */
    int allocateRawMemory (int size, int alignment) {
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
    int allocateRawMemory (int size, int alignment, int offset) {
	VM_Magic.pragmaInline();
	lock.lock();
	
	// reserve space for offset bytes
	allocationCursor += offset;
	// align the interior portion of the requested space
	allocationCursor = VM_Memory.align(allocationCursor, alignment);
	// allocate remaining space and 
	int result = internalAllocate(size - offset) - offset;

	lock.unlock();
	return result;
    }



    /**
     * Allocate a chunk of memory of a given size.  Always inlined.
     * Internal version does no locking.
     *   @param size Number of bytes to allocate
     *   @return Address of allocated storage
     */
    private int internalAllocate (int size) {
	VM_Magic.pragmaInline();
	
	int result = allocationCursor;
	allocationCursor += size;
	if (allocationCursor > highAddress)
	    VM.sysFail("Immortal heap space exhausted");
	return result;
    }

}
