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
package com.ibm.JikesRVM.memoryManagers;

import VM;
import VM_Magic;
import VM_Address;
import VM_Processor;
import VM_PragmaNoInline;
import VM_PragmaInline;
import VM_Address;
import VM_Memory;
import VM_Scheduler;
import VM_Thread;
import VM_PragmaUninterruptible;

final class VM_Chunk implements VM_GCConstants {
  
  /**
   * How big are the chunks?
   */
  private static final int CHUNK_SIZE = 64 * 1024;

  /**
   * Some accessor functions.
   */
  public static VM_Address currentChunk1() throws VM_PragmaUninterruptible {
      return VM_Processor.getCurrentProcessor().currentChunk1;
  }

  public static VM_Address currentChunk2() throws VM_PragmaUninterruptible {
      return VM_Processor.getCurrentProcessor().currentChunk2;
  }


  /** 
   * Allocate zeroed memory of size bytes.
   * Memory will be allocated from local chunk1.<p>
   * If there is insufficient memory available then 
   * a new chunk will be acquired from backingHeap1 and 
   * the memory will be allocated from it. <p>
   * 
   * NOTE: This code sequence is carefully written to generate
   *       optimal code when inlined by the optimzing compiler.  
   *       If you change it you must verify that the efficient 
   *       inlined allocation sequence isn't hurt!
   */
  public static VM_Address allocateChunk1(int size) throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
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
   * This memory is not zeroed because chunk2 is used
   * by the collectors to copy objects and thus does not need
   * to be zeroed.<p>
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
  public static VM_Address allocateChunk2(int size) throws OutOfMemoryError, VM_PragmaInline, VM_PragmaUninterruptible {
    VM_Address oldCurrent = VM_Processor.getCurrentProcessor().currentChunk2;
    VM_Address newCurrent = oldCurrent.add(size);
    if (newCurrent.LE(VM_Processor.getCurrentProcessor().endChunk2)) {
      VM_Processor.getCurrentProcessor().currentChunk2 = newCurrent;
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
				 boolean getChunk) throws VM_PragmaUninterruptible, OutOfMemoryError {
    st.backingHeapChunk1 = h;
    if (getChunk) {
      VM_Address chunk = h.allocateRawMemory(CHUNK_SIZE);
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
				 boolean getChunk) throws OutOfMemoryError, VM_PragmaUninterruptible {
    st.backingHeapChunk2 = h;
    if (getChunk) {
      VM_Address chunk = h.allocateRawMemory(CHUNK_SIZE);
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
   * Promote chunk2 to chunk1; if ZERO_CHUNKS_ON_ALLOCATION,
   * then it zeros the remaining free space of the chunk (which hadn't been zeroed
   * before because the GC system was using it to copy objects).
   *
   * @param st the virtual processor whose chunk2 should be promoted to chunk1
   */
  public static void promoteChunk2(VM_Processor st) throws VM_PragmaUninterruptible {
    st.startChunk1 = st.startChunk2;
    st.currentChunk1 = st.currentChunk2;
    st.endChunk1 = st.endChunk2;
    st.backingHeapChunk1 = st.backingHeapChunk2;
    
    st.startChunk2 = VM_Address.zero();
    st.currentChunk2 = VM_Address.zero();
    st.endChunk2 = VM_Address.zero();
    st.backingHeapChunk2 = null;

    if (!st.startChunk1.isZero() && ZERO_CHUNKS_ON_ALLOCATION) {
      VM_Memory.zero(st.currentChunk1, st.endChunk1);
    }
  }

  public static boolean unusedChunk1(VM_Processor st) throws VM_PragmaUninterruptible {
      return st.startChunk1 == st.currentChunk1;
  }

  public static boolean unusedChunk2(VM_Processor st) throws VM_PragmaUninterruptible {
      return st.startChunk2 == st.currentChunk2;
  }

  /**
   * How much free space is left in the chunk1's?
   */
  public static int freeMemoryChunk1() throws VM_PragmaUninterruptible {
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
  private static VM_Address slowPath1(int size) throws OutOfMemoryError, VM_PragmaNoInline, VM_PragmaUninterruptible {
    // Detect Java-level allocations during GC or while GC disabled 
    // and trap as a fatal error.
    if (VM.VerifyAssertions) {
      VM_Thread t = VM_Thread.getCurrentThread();
      VM.assert(!VM_Allocator.gcInProgress);
      VM.assert(!t.disallowAllocationsByThisThread);
    }

    // Try to get a chunk from the backing heap.
    int count = 0;
    while (true) {
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
      VM_Address chunk = st.backingHeapChunk1.allocateRawMemory(CHUNK_SIZE);
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
      VM_Allocator.heapExhausted(st.backingHeapChunk1, size, count++);
    }
  }

  /**
   * Slow path allocation; chunk2 is exhausted so
   * we must acquire a new chunk from the backing heap
   * and then allocate the memory from it. 
   * This may trigger GC
   * 
   * @param size the number of bytes to allocate
   */
  private static VM_Address slowPath2(int size) throws OutOfMemoryError, VM_PragmaUninterruptible {
    // Try to get a chunk from the backing heap.
    int count = 0;
    while (true) {
      VM_Processor st = VM_Processor.getCurrentProcessor();
      VM_Address oldCurrent = st.currentChunk2;
      VM_Address newCurrent = oldCurrent.add(size);
      if (newCurrent.LE(st.endChunk2)) {
	st.currentChunk2 = newCurrent;
	return oldCurrent;
      }
      VM_Address chunk = st.backingHeapChunk2.allocateRawMemory(CHUNK_SIZE);
      if (!chunk.isZero()) {
	// Normal case; we successfully got a chunk.  
	// Update our data structures and allocate size bytes from it
	st.startChunk2 = chunk;
	st.currentChunk2 = chunk.add(size);
	st.endChunk2 = chunk.add(CHUNK_SIZE);
	return chunk;
      }
      // Backing heap exhausted; let the allocator attempt
      // to do something (usual some kind of GC) to handle the situation.  
      // However, this in the JikesRVM allocators this is pretty much
      // a fatal error, so we don't expect this call to return.
      VM_Allocator.heapExhausted(st.backingHeapChunk2, size, count++);
    }
  }
}
