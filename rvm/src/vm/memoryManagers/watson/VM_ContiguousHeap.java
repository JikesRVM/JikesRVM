/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * A heap that allocates in contiguous free
 * memory by bumping a pointer on each allocation.
 * The pointer bump is down with an atomic
 * fetch and add sequence, so this heap is 
 * multi-processor safe. <p>
 * 
 * For reasonable performance in an MP system,
 * most allocations are done in processor local
 * chunks (VM_Chunk.java) and the contiguous heap
 * is only used to acquire new chunks.
 * 
 * @author Perry Cheng
 * @author Dave Grove
 * @author Stephen Smith
 * 
 * @see VM_Chunk
 * @see VM_Processor
 */
final class VM_ContiguousHeap extends VM_Heap
  implements VM_Uninterruptible, VM_GCConstants {

  /**
   * The current allocation pointer.
   * Always updated with atomic operations!
   */
  private VM_Address current;

  VM_ContiguousHeap(String s) {
    super(s);
  }

  /** 
   * Allocate raw memory of size bytes.
   * Important the caller of this function may be responsible 
   * for zeroing memory if required! The allocated memory is
   * intentionally not zeroed here.
   * 
   * @param size the number of bytes to allocate
   * @return the allocate memory or VM_Address.zero() if space is exhausted.
   */
  public VM_Address allocate(int size) {
    int offset = VM_Entrypoints.contiguousHeapCurrentField.getOffset();
    VM_Address addr =  VM_Synchronization.fetchAndAddAddressWithBound(this, offset, size, end);
    if (addr.isMax()) return VM_Address.zero();
    return addr;
  }    

  /**
   * All space in the heap is available for allocation again.
   */
  public void reset() {
    current = start;
  }

  /**
   * Zero the remaining free space in the heap.
   */
  public void zeroFreeSpace() {
    VM_Memory.zeroPages(current, end.diff(current));
  }

  /**
   * Zero the remaining free space in the heap.
   */
  public void zeroFreeSpaceParallel() {
    zeroParallel(current, end);
  }


  /**
   * Round up to page boundary
   */
  public void roundUpPage() {
    current = VM_Memory.roundUpPage(current);
  }

  /**
   * How much free memory is left in the heap?
   */
  public int freeMemory() {
    return end.diff(current);
  }
}
