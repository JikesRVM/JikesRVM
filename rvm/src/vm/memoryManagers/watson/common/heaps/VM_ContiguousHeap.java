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


  final static int FORWARD = 0;
  final static int BACKWARD = 1;

  /**
   * The current allocation pointer.
   * Always updated with atomic operations!
   */
  private VM_Address current;
  private VM_Address saved;
  private int sense;

  VM_ContiguousHeap(String s) {
    super(s);
    sense = FORWARD;
  }

  int sense() { return sense; }

  /**
   * Allocate size bytes of raw memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory(int size) {
    // The issue is that this doesn't make much sense because
    // VM_Heap requires that this returns valid memory and if this
    // heap instance is full what allocateRawMemory is going to do is 
    // trigger a GC, which may reverse the sense of toSpace and fromSpace.
    // When this happens, allocating the memory in this heap instance
    // makes no sense, since it has logically switched to another heap.
    // There is probably a better way to handle this, but for now we'll just fail
    // since we don't expect anyone to every do this.
    VM.sysFail("allocateZeroedMemory on VM_Contiguous heap forbidden");
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
   * Allocate raw memory of size bytes.
   * Important the caller of this function may be responsible 
   * for zeroing memory if required! The allocated memory is
   * intentionally not zeroed here.
   * 
   * @param size the number of bytes to allocate
   * @return the allocate memory or VM_Address.zero() if space is exhausted.
   */
  public VM_Address allocateRawMemory(int size) {
    int offset = VM_Entrypoints.contiguousHeapCurrentField.getOffset();
    if (sense == FORWARD) {
	VM_Address addr = VM_Synchronization.fetchAndAddAddressWithBound(this, offset, size, end);
	if (VM.VerifyAssertions) VM.assert(start.LE(current) && current.LE(end));
	if (!addr.isMax()) return addr;
    }
    else {
	VM_Address addr = VM_Synchronization.fetchAndSubAddressWithBound(this, offset, size, start);
	if (VM.VerifyAssertions) VM.assert(start.LE(current) && current.LE(end));
	if (!addr.isMax()) return addr.sub(size);
    }
    return VM_Address.zero();
  }    

  VM_Address current() { return current; }

  public void show(boolean newline) {
      super.show(false);
      VM.sysWrite("   cur = ");
      VM.sysWrite(current);
      VM.sysWrite((sense == FORWARD) ? " -->" : " <--");
      VM.sysWrite("    ", usedMemory() / 1024, "  Kb used");
      if (newline) VM.sysWriteln();
  }

  /**
   * All space in the heap is available for allocation again.
   */
  public void reset() {
    if (sense == FORWARD)
      saved = current = start;
    else
      saved = current = end;
  }

  /**
   * All space in the heap is available for allocation again.
   */
  public void setRegion(VM_Address s, VM_Address e) {
      super.setRegion(s, e);
      reset();
  }

  public void setRegion(VM_Address s, VM_Address e, int se) {
      if (VM.VerifyAssertions) VM.assert(se == FORWARD || se == BACKWARD);
      sense = se;
      setRegion(s, e);
  }

  public void setRegion(VM_Address s, VM_Address c, VM_Address e, int se) {
      if (VM.VerifyAssertions) VM.assert(se == FORWARD || se == BACKWARD);
      sense = se;
      setRegion(s, e);
      current = c;
  }

  public void contractRegion() {
      if (sense == FORWARD) {
	  end = current;
	  setAuxiliary();
      }
      else {
	  start = current;
	  setAuxiliary();
      }
  }

  public void extendRegion(VM_Address newBoundary) {
      if (sense == FORWARD) {
	  if (VM.VerifyAssertions) VM.assert(newBoundary.GE(end));
	  end = newBoundary;
	  setAuxiliary();
      }
      else {
	  if (VM.VerifyAssertions) VM.assert(newBoundary.LE(start));
	  start = newBoundary;
	  setAuxiliary();
      }
  }

  /**
   * Heap is reset at attachment and detachment.
   */
  public void attach(int size) {
    super.attach(size);
    reset();
  }

  public void detach(int size) {
    super.detach();
    reset();
  }

  public int allocatedFromSaved() {
      if (sense == FORWARD)
	  return current.diff(saved);
      else
	  return saved.diff(current);
  }

  public void recordSaved() {
    saved = current;
  }

  /**
   * Zero the remaining free space in the heap.
   */
  public void zeroFreeSpace() {
      if (sense == FORWARD)
	  VM_Memory.zeroPages(current, end.diff(current));
      else
	  VM_Memory.zeroPages(start, current.diff(start));
  }

  /**
   * Zero the remaining free space in the heap.
   */
  public void zeroFreeSpaceParallel() {
    if (sense == FORWARD)
	zeroParallel(current, end);
    else
	zeroParallel(start, current);
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
    if (sense == FORWARD)
	return end.diff(current);
    else
	return current.diff(start);
  }

  /**
   * How much memory is used in the heap?
   */
  public int usedMemory() {
    if (sense == FORWARD)
	return current.diff(start);
    else
	return end.diff(current);
  }
}
