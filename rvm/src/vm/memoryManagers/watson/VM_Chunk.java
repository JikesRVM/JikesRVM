/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Fast path allocation using pointer bumping. 
 * This class contains static methods that 
 * manipulated the Chunk1 and Chunk2 "objects"
 * of VM_Processor.  We hand inline the chunk objects
 * into the VM_Processor object to avoid an extra dependent 
 * load instruction on the fast path allocation sequence.
 * In an ideal world, object inlining would do this for us.
 * 
 * @author Perry Cheng
 * @author Dave Grove
 * @author Stephen Smith
 */
final class VM_Chunk implements VM_Uninterruptible, VM_GCConstants {
  
  /**
   * How big are the chunks?
   */
  private static final int CHUNK_SIZE = 64 * 1024;

  /**
   * Some accessor functions.
   */
  public static VM_Address currentChunk1() {
      return VM_Processor.getCurrentProcessor().currentChunk1;
  }

  public static VM_Address currentChunk2() {
      return VM_Processor.getCurrentProcessor().currentChunk2;
  }


  /** 
   * Allocate raw memory of size bytes.
   * Memory will be allocated from local chunk1.
   * If there is insufficient memory available then 
   * a new chunk will be acquired from backingHeap1 and 
   * the memory will be allocated from it. <p>
   * 
   * NOTE: This code sequence is carefully written to generate
   *       optimal code when inlined by the optimzing compiler.  
   *       If you change it you must verify that the efficient 
   *       inlined allocation sequence isn't hurt!
   */
  public static VM_Address allocateChunk1(int size) throws OutOfMemoryError {
    VM_Magic.pragmaInline();
    VM_Address oldCurrent = VM_Processor.getCurrentProcessor().currentChunk1;
    VM_Address newCurrent = oldCurrent.add(size);
    if (newCurrent.LE(VM_Processor.getCurrentProcessor().endChunk1)) {
      VM_Processor.getCurrentProcessor().currentChunk1 = newCurrent;
      // if the backing heap didn't zero the chunk when it gave it to
      // us, then we must zero it now.
      if (!ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(oldCurrent, size);
      return oldCurrent;
    }
    return slowPath1(size);
  }

  /** 
   * Allocate raw memory of size bytes.
   * Memory will be allocated from local chunk2.
   * if there is insufficient memory available then 
   * a new chunk will be acquired from backingHeap2 and 
   * the memory will be allocated from it. <p>
   * 
   * NOTE: This code sequence is carefully written to generate
   *       optimal code when inlined by the optimzing compiler.  
   *       If you change it you must verify that the efficient 
   *       inlined allocation sequence isn't hurt!
   */
  public static VM_Address allocateChunk2(int size) throws OutOfMemoryError {
    VM_Magic.pragmaInline();
    VM_Address oldCurrent = VM_Processor.getCurrentProcessor().currentChunk2;
    VM_Address newCurrent = oldCurrent.add(size);
    if (newCurrent.LE(VM_Processor.getCurrentProcessor().endChunk2)) {
      VM_Processor.getCurrentProcessor().currentChunk2 = newCurrent;
      // if the backing heap didn't zero the chunk when it gave it to
      // us, then we must zero it now.
      if (!ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(oldCurrent, size);
      return oldCurrent;
    }
    return slowPath2(size);
  }

  /**
   * Reset the backing heap for chunk1 and optionally acquire 
   * a chunk from the new backing heap.
   * 
   * @param st the VM_Processors whose chunk should be reset
   * @param h the new backing heap for chunk1
   * @param getChunk should we immediately acquire a chunk?
   */
  public static void resetChunk1(VM_Processor st, 
				 VM_ContiguousHeap h, 
				 boolean getChunk) throws OutOfMemoryError {
    st.backingHeapChunk1 = h;
    if (getChunk) {
      VM_Address chunk = h.allocate(CHUNK_SIZE);
      if (!chunk.isZero()) {
	st.startChunk1 = chunk;
	st.currentChunk1 = chunk;
	st.endChunk1 = chunk.add(CHUNK_SIZE);
	return;
      }
    }
    st.startChunk1 = VM_Address.zero();
    st.currentChunk1 = VM_Address.zero();
    st.endChunk1 = VM_Address.zero();
  }

  /**
   * Reset the backing heap for chunk1 and optionally acquire 
   * a chunk from the new backing heap.
   * 
   * @param st the VM_Processors whose chunk should be reset
   * @param h the new backing heap for chunk1
   * @param getChunk should we immediately acquire a chunk?
   */
  public static void resetChunk2(VM_Processor st, 
				 VM_ContiguousHeap h, 
				 boolean getChunk) throws OutOfMemoryError {
    st.backingHeapChunk2 = h;
    if (getChunk) {
      VM_Address chunk = h.allocate(CHUNK_SIZE);
      if (!chunk.isZero()) {
	st.startChunk2 = chunk;
	st.currentChunk2 = chunk;
	st.endChunk2 = chunk.add(CHUNK_SIZE);
	return;
      }
    }
    st.startChunk2 = VM_Address.zero();
    st.currentChunk2 = VM_Address.zero();
    st.endChunk2 = VM_Address.zero();
  }

  /**
   * Swap the chunks of given virtual processor
   * @param st the virtual processor whose chunks should be swapped.
   */
  public static void swapChunks(VM_Processor st) {
    VM_Address start = st.startChunk1;
    VM_Address current = st.currentChunk1;
    VM_Address end = st.endChunk1;
    VM_ContiguousHeap heap = st.backingHeapChunk1;

    st.startChunk1 = st.startChunk2;
    st.currentChunk1 = st.currentChunk2;
    st.endChunk1 = st.endChunk2;
    st.backingHeapChunk1 = st.backingHeapChunk2;

    st.startChunk2 = start;
    st.currentChunk2 = current;
    st.endChunk2 = end;
    st.backingHeapChunk2 = heap;
  }


  public static boolean unusedChunk1(VM_Processor st) {
      return st.startChunk1 == st.currentChunk1;
  }

  public static boolean unusedChunk2(VM_Processor st) {
      return st.startChunk2 == st.currentChunk2;
  }

  /**
   * How much free space is left in the chunk1's?
   */
  public static int freeMemoryChunk1() {
    int sum = 0;
    for (int i=0; i<VM_Scheduler.numProcessors; i++) {
      VM_Processor st = VM_Scheduler.processors[i+1];
      if (st != null) {
	sum += st.endChunk1.diff(st.currentChunk1);
      }
    }
    return sum;
  }

  /**
   * Slow path allocation; chunk1 is exhausted so
   * we must acquire a new chunk from the backing heap
   * and then allocate the memory from it. 
   * This may trigger GC
   * 
   * @param size the number of bytes to allocate
   */
  private static VM_Address slowPath1(int size) throws OutOfMemoryError {
    // Detect Java-level allocations during GC or while GC disabled 
    // and trap as a fatal error.
    VM_Magic.pragmaNoInline();
    if (VM.VerifyAssertions) {
      VM_Thread t = VM_Thread.getCurrentThread();
      VM.assert(!VM_Allocator.gcInProgress);
      VM.assert(!t.disallowAllocationsByThisThread);
    }

    // Try to get a chunk from the backing heap.
    // We try several times before giving up to 
    // deal with the fact that this thread may not be 
    // the first thread scheduled after the allocator
    // attempts to handle the situation and get us some memory.
    for (int i=0; i<3; i++) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      VM_Address oldCurrent = st.currentChunk1;
      VM_Address newCurrent = oldCurrent.add(size);
      if (newCurrent.LE(st.endChunk1)) {
	st.currentChunk1 = newCurrent;
	// if the backing heap didn't zero the chunk when it gave it to
	// us, then we must zero it now.
	if (!ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(oldCurrent, size);
	return oldCurrent;
      }
      VM_Address chunk = st.backingHeapChunk1.allocate(CHUNK_SIZE);
      if (!chunk.isZero()) {
	// Normal case; we successfully got a chunk.  
	// Update our data structures and allocate size bytes from it
	if (ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroPages(chunk, CHUNK_SIZE);
	st.startChunk1 = chunk;
	st.currentChunk1 = chunk.add(size);
	st.endChunk1 = chunk.add(CHUNK_SIZE);
	return chunk;
      }
      // Backing heap exhausted; let the allocator attempt
      // to do something (usual some kind of GC) to handle the situation.  
      // Then back to the top of the loop to try again.
      VM_Allocator.chunk1BackingHeapExhausted(size);
    }
    VM_Allocator.outOfMemory(size);
    return VM_Address.zero(); // placate Jikes (actually unreacable);
  }

  /**
   * Slow path allocation; chunk2 is exhausted so
   * we must acquire a new chunk from the backing heap
   * and then allocate the memory from it. 
   * This may trigger GC
   * 
   * @param size the number of bytes to allocate
   */
  private static VM_Address slowPath2(int size) throws OutOfMemoryError {
    // Try to get a chunk from the backing heap.
    // We try several times before giving up to 
    // deal with the fact that this thread may not be 
    // the first thread scheduled after the allocator
    // attempts to handle the situation and get us some memory.
    for (int i=0; i<3; i++) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      VM_Address oldCurrent = st.currentChunk2;
      VM_Address newCurrent = oldCurrent.add(size);
      if (newCurrent.LE(st.endChunk2)) {
	st.currentChunk2 = newCurrent;
	// if the backing heap didn't zero the chunk when it gave it to
	// us, then we must zero it now.
	if (!ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroTemp(oldCurrent, size);
	return oldCurrent;
      }
      VM_Address chunk = st.backingHeapChunk2.allocate(CHUNK_SIZE);
      if (!chunk.isZero()) {
	// Normal case; we successfully got a chunk.  
	// Update our data structures and allocate size bytes from it
	if (ZERO_CHUNKS_ON_ALLOCATION) VM_Memory.zeroPages(chunk, CHUNK_SIZE);
	st.startChunk2 = chunk;
	st.currentChunk2 = chunk.add(size);
	st.endChunk2 = chunk.add(CHUNK_SIZE);
	return chunk;
      }
      // Backing heap exhausted; let the allocator attempt
      // to do something (usual some kind of GC) to handle the situation.  
      // Then back to the top of the loop to try again.
      VM_Allocator.chunk2BackingHeapExhausted(size);
    }
    VM_Allocator.outOfMemory(size);
    return VM_Address.zero(); // placate Jikes (actually unreacable);
  }
}
