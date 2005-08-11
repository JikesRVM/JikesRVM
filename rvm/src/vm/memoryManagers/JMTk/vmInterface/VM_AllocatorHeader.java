/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.vm.ActivePlan;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.BootImageInterface;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see com.ibm.JikesRVM.VM_ObjectModel
 * 
 * @author Perry Cheng
 */
public final class VM_AllocatorHeader implements VM_Constants {
  public static final boolean STEAL_NURSERY_GC_HEADER = false;
  // not supported during expected transition to new object model.
  public static final boolean STEAL_NURSERY_SCALAR_GC_HEADER = false;
  public static final boolean NEEDS_LINEAR_SCAN = ActivePlan.constraints().needsLinearScan();

  public static final int REQUESTED_BITS = ActivePlan.constraints().gcHeaderBits();
  public static final int NUM_BYTES_HEADER = ActivePlan.constraints().gcHeaderWords() << LOG_BYTES_IN_WORD;

  /**
   * Override the boot-time initialization method here, so that
   * the core JMTk code doesn't need to know about the 
   * BootImageInterface type.
   */
  public static void initializeHeader(BootImageInterface bootImage, Offset ref,
                                      Object[] tib, int size, boolean isScalar)
    throws InterruptiblePragma {
    //    int status = VM_JavaHeader.readAvailableBitsWord(bootImage, ref);
    Word status = ActivePlan.global().setBootTimeGCBits(ref, 
      ObjectReference.fromObject(tib), size, Word.zero());
    VM_JavaHeader.writeAvailableBitsWord(bootImage, ref, status);
  }

  public static void dumpHeader(Object ref) throws UninterruptiblePragma {
    // currently unimplemented
    //    Header.dumpHeader(VM_Magic.objectAsAddress(ref));
  }

}
