/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers;

import VM_Constants;
import VM_ProcessorLock;
import VM_Address;
import VM_Memory;
import VM_ObjectModel;
import VM;
import VM_Magic;
import VM_PragmaUninterruptible;

/**
 * @author Dave Grove
 */
final class VM_BootHeap extends VM_Heap
  implements VM_AllocatorHeaderConstants {

  VM_BootHeap() throws VM_PragmaUninterruptible {
    super("Boot Image Heap");
    if (USE_SIDE_MARK_VECTOR) {
      markVector = new VM_SideMarkVector();
    }
  }

  /**
   * the current mark value
   */
  private int markValue;

  /**
   * A side mark vector, in case object model doesn't have mark bit in object
   */
  private VM_SideMarkVector markVector;

  void setAuxiliary() throws VM_PragmaUninterruptible {
    super.setAuxiliary();
    if (USE_SIDE_MARK_VECTOR) {
      markVector.boot(mallocHeap, start, end);
    }
  }

  /**
   * Allocate size bytes of raw memory.
   * Size is a multiple of wordsize, and the returned memory must be word aligned
   * 
   * @param size Number of bytes to allocate
   * @return Address of allocated storage
   */
  protected VM_Address allocateZeroedMemory(int size) throws VM_PragmaUninterruptible {
    // Can't allocate anything in the bootheap!
    VM.sysFail("allocateZeroedMemory on VM_BootHeap forbidden");
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
   * Mark an object in the boot heap
   * @param ref the object reference to mark
   * @return whether or not the object was already marked
   */
  public boolean mark(VM_Address ref) throws VM_PragmaUninterruptible {
    if (USE_SIDE_MARK_VECTOR) {
      return markVector.testAndMark(ref, markValue);
    } else {
      return VM_AllocatorHeader.testAndMark(VM_Magic.addressAsObject(ref), markValue);
    }
  }

  /**
   * Is the object reference live?
   */
  public boolean isLive(VM_Address ref) throws VM_PragmaUninterruptible {
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
  public void startCollect() throws VM_PragmaUninterruptible {
    // flip the sense of the mark bit.
    markValue = markValue ^ VM_CommonAllocatorHeader.GC_MARK_BIT_MASK;
  }    
}
