/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


package com.ibm.JikesRVM.memoryManagers;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Memory;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * A container heap with 3 contiguous heaps next to each other
 *   and are adjusted in an Appel-style generational collector.
 * 
 * 
 * @author Perry Cheng
 * 
 * @see VM_Chunk
 * @see VM_Processor
 */
final class VM_AppelHeap extends VM_Heap
  implements VM_GCConstants {

  VM_ContiguousHeap nurseryHeap;
  VM_ContiguousHeap fromHeap;
  VM_ContiguousHeap toHeap;

  VM_AppelHeap(String s) throws VM_PragmaUninterruptible {
    super(s);
  }

  /**
   * Allocate size bytes of raw memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory(int size) throws VM_PragmaUninterruptible {
    // We're just a container for other heaps, thus not supported!
    VM.sysFail("allocateZeroedMemory on VM_AppelHeap forbidden");
    return VM_Address.zero();
  }

  /**
   * Hook to allow heap to perform post-allocation processing of the object.
   * For example, setting the GC state bits in the object header.
   */
  protected void postAllocationProcessing(Object newObj) throws VM_PragmaUninterruptible { 
    // nothing to do in this heap
  }

  /**
   * Heap is reset at attachment and detachment.
   */
  public void attach(int size,
		     VM_ContiguousHeap nursery,
		     VM_ContiguousHeap from,
		     VM_ContiguousHeap to) throws VM_PragmaUninterruptible {
    super.attach(size);
    nurseryHeap = nursery;
    fromHeap = from;
    toHeap = to;
    VM_Address middle = VM_Memory.roundDownPage(start.add(size / 2));
    nurseryHeap.setRegion(start, middle, VM_ContiguousHeap.FORWARD);
    fromHeap.setRegion(middle, end, VM_ContiguousHeap.BACKWARD);
    toHeap.setRegion(end, end, VM_ContiguousHeap.BACKWARD);
  }

  public void minorStart() throws VM_PragmaUninterruptible {
      // nothing needed
  }

  public void minorEnd() throws VM_PragmaUninterruptible {
      VM_Address middle = VM_Memory.roundDownPage(start.add(end.diff(start) / 2));
      if (nurseryHeap.sense() == VM_ContiguousHeap.FORWARD) {
	  int available = fromHeap.current().diff(middle);
	  if (VM.VerifyAssertions) VM.assert(available > 0);
	  VM_Address newMiddle = nurseryHeap.start.add(available);
	  nurseryHeap.setRegion(nurseryHeap.start, newMiddle, VM_ContiguousHeap.FORWARD);
	  fromHeap.extendRegion(newMiddle);
      }
      else {
	  int available = middle.diff(fromHeap.current());
	  if (VM.VerifyAssertions) VM.assert(available > 0);
	  VM_Address newMiddle = nurseryHeap.end.sub(available);
	  nurseryHeap.setRegion(newMiddle, nurseryHeap.end, VM_ContiguousHeap.BACKWARD);
	  fromHeap.extendRegion(newMiddle);
      }
  }

  public void show(String when) throws VM_PragmaUninterruptible {
      VM.sysWriteln(when);
      show();
      VM.sysWriteln();
  }

  public void show() throws VM_PragmaUninterruptible {
      super.show();
      nurseryHeap.show();
      fromHeap.show();
      toHeap.show();
  }

  public void majorStart() throws VM_PragmaUninterruptible {
      if (nurseryHeap.sense() == VM_ContiguousHeap.FORWARD) {
	  VM_Address newToStart = nurseryHeap.start;
	  VM_Address newToEnd = fromHeap.current();
	  VM_Address newFromStart = newToEnd;
	  VM_Address newFromEnd = fromHeap.end;
	  fromHeap.setRegion(newFromStart, newFromEnd, newFromEnd, VM_ContiguousHeap.FORWARD);
	  toHeap.setRegion(newToStart, newToEnd, VM_ContiguousHeap.FORWARD);
	  nurseryHeap.setRegion(newFromEnd, newFromEnd, newFromEnd, VM_ContiguousHeap.BACKWARD);
      }
      else {
	  VM_Address newToStart = fromHeap.current();
	  VM_Address newToEnd = nurseryHeap.end;
	  VM_Address newFromStart = fromHeap.start;
	  VM_Address newFromEnd = fromHeap.current();
	  fromHeap.setRegion(newFromStart, newFromStart, newFromEnd, VM_ContiguousHeap.BACKWARD);
	  toHeap.setRegion(newToStart, newToEnd, VM_ContiguousHeap.BACKWARD);
	  nurseryHeap.setRegion(newFromStart, newFromStart, VM_ContiguousHeap.FORWARD);
      }
  }

  public void majorEnd() throws VM_PragmaUninterruptible {
      if (toHeap.sense() == VM_ContiguousHeap.FORWARD) {
	  VM_Address newFromStart = toHeap.start;
	  VM_Address newFromEnd = toHeap.current();
	  VM_Address newNurseryStart = toHeap.current();
	  VM_Address newNurseryEnd = fromHeap.end;
	  toHeap.setRegion(newFromStart, newFromStart, VM_ContiguousHeap.FORWARD);
	  fromHeap.setRegion(newFromStart, newFromEnd, newFromEnd, VM_ContiguousHeap.FORWARD);
	  nurseryHeap.setRegion(newNurseryStart, newNurseryEnd, VM_ContiguousHeap.BACKWARD);
      }
      else {
	  VM_Address newFromStart = toHeap.current();
	  VM_Address newFromEnd = toHeap.end;
	  VM_Address newNurseryStart = fromHeap.start;
	  VM_Address newNurseryEnd = toHeap.current();
	  nurseryHeap.setRegion(newNurseryStart, newNurseryEnd, VM_ContiguousHeap.FORWARD);
	  fromHeap.setRegion(newFromStart, newFromStart, newFromEnd, VM_ContiguousHeap.BACKWARD);
	  toHeap.setRegion(newFromStart, newFromStart, VM_ContiguousHeap.BACKWARD);
      }
      minorEnd();
  }

  public void detach(int size) throws VM_PragmaUninterruptible {
      VM.assert(false);
  }

  public void zeroFreeSpace() throws VM_PragmaUninterruptible { VM.assert(false); }
  public void zeroFreeSpaceParallel() throws VM_PragmaUninterruptible { VM.assert(false); }
  public int freeMemory() throws VM_PragmaUninterruptible { VM.assert(false); return 0; }

}
