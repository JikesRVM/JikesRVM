/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_Memory;

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author David Bacon
 * @author Steve Fink
 * @author Dave Grove
 */
public class CopyingBarrierHeader extends CopyingHeader {

  public static final int GC_MARK_BIT_MASK    = 0x1;
  public static final int GC_BARRIER_BIT_IDX  = 1;
  public static final int GC_BARRIER_BIT_MASK = (1 << GC_BARRIER_BIT_IDX);

  /*
   * Barrier Bit -- only used when VM_Allocator.NEEDS_WRITE_BARRIER.
   */

  /**
   * test to see if the barrier bit is set
   */
  static boolean testBarrierBit(Object ref) throws VM_PragmaUninterruptible {
    return VM_ObjectModel.testAvailableBit(ref, GC_BARRIER_BIT_IDX);
  }

  /**
   * clear the barrier bit (indicates that object is in write buffer)
   */
  static void clearBarrierBit(Object ref) throws VM_PragmaUninterruptible {
    VM_ObjectModel.setAvailableBit(ref, GC_BARRIER_BIT_IDX, false);
  }

  /**
   * set the barrier bit (indicates that object needs to be put in write buffer
   * if a reference is stored into it).
   */
  static void setBarrierBit(Object ref) throws VM_PragmaUninterruptible {
    VM_ObjectModel.setAvailableBit(ref, GC_BARRIER_BIT_IDX, true);
  }


  /**
   * test to see if the mark bit has the given value
   */
  static boolean testMarkBit(Object ref, int value) throws VM_PragmaUninterruptible {
    return (VM_ObjectModel.readAvailableBitsWord(ref) & value) != 0;
  }

  /**
   * write the given value in the mark bit.
   */
  static void writeMarkBit(Object ref, int value) throws VM_PragmaUninterruptible {
    int oldValue = VM_ObjectModel.readAvailableBitsWord(ref);
    int newValue = (oldValue & ~GC_MARK_BIT_MASK) | value;
    VM_ObjectModel.writeAvailableBitsWord(ref, newValue);
  }

  /**
   * atomically write the given value in the mark bit.
   */
  static void atomicWriteMarkBit(Object ref, int value) throws VM_PragmaUninterruptible {
    while (true) {
      int oldValue = VM_ObjectModel.prepareAvailableBits(ref);
      int newValue = (oldValue & ~GC_MARK_BIT_MASK) | value;
      if (VM_ObjectModel.attemptAvailableBits(ref, oldValue, newValue)) break;
    }
  }

}
