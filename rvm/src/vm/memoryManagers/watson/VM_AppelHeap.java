/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
  implements VM_Uninterruptible, VM_GCConstants {

  VM_ContiguousHeap nurseryHeap;
  VM_ContiguousHeap fromHeap;
  VM_ContiguousHeap toHeap;

  VM_AppelHeap(String s) {
    super(s);
  }

  public VM_Address allocate(int size) { VM.assert(false); return VM_Address.zero(); }

  /**
   * Heap is reset at attachment and detachment.
   */
  public void attach(int size,
		     VM_ContiguousHeap nursery,
		     VM_ContiguousHeap from,
		     VM_ContiguousHeap to) {
    super.attach(size);
    nurseryHeap = nursery;
    fromHeap = from;
    toHeap = to;
    VM_Address middle = VM_Memory.roundDownPage(start.add(size / 2));
    nurseryHeap.setRegion(start, middle, VM_ContiguousHeap.FORWARD);
    fromHeap.setRegion(middle, end, VM_ContiguousHeap.BACKWARD);
    toHeap.setRegion(end, end, VM_ContiguousHeap.BACKWARD);
  }

  public void minorStart() {
      // nothing needed
  }

  public void minorEnd() {
      if (nurseryHeap.sense() == VM_ContiguousHeap.FORWARD) {
	  VM_Address start = nurseryHeap.start;
	  VM_Address end = fromHeap.current();
	  VM_Address middle = VM_Memory.roundDownPage(start.add(end.diff(start) / 2));
	  nurseryHeap.setRegion(start, middle, VM_ContiguousHeap.FORWARD);
	  fromHeap.extendRegion(middle);
      }
      else {
	  VM_Address start = fromHeap.current();
	  VM_Address end = nurseryHeap.end;
	  VM_Address middle = VM_Memory.roundUpPage(start.add(end.diff(start) / 2));
	  nurseryHeap.setRegion(middle, end, VM_ContiguousHeap.BACKWARD);
	  fromHeap.extendRegion(middle);
      }
  }

  public void show(String when) {
      VM.sysWriteln(when);
      show();
      VM.sysWriteln();
  }

  public void show() {
      super.show();
      nurseryHeap.show();
      fromHeap.show();
      toHeap.show();
  }

  public void majorStart() {
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

  public void majorEnd() {
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

  public void detach(int size) {
      VM.assert(false);
  }

  public void zeroFreeSpace() { VM.assert(false); }
  public void zeroFreeSpaceParallel() { VM.assert(false); }
  public int freeMemory() { VM.assert(false); return 0; }

}
