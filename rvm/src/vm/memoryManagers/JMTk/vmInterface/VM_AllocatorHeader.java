/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.mmInterface;

import org.mmtk.plan.Plan;

import com.ibm.JikesRVM.VM_JavaHeader;
import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Word;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see com.ibm.JikesRVM.VM_ObjectModel
 * 
 * @author Perry Cheng
 */
public final class VM_AllocatorHeader {
  public static final boolean STEAL_NURSERY_GC_HEADER = Plan.STEAL_NURSERY_GC_HEADER;
  // not supported during expected transition to new object model.
  public static final boolean STEAL_NURSERY_SCALAR_GC_HEADER = false;

  public static final int REQUESTED_BITS = Plan.GC_HEADER_BITS_REQUIRED;
  public static final int NUM_BYTES_HEADER = Plan.GC_HEADER_BYTES_REQUIRED;

  /**
   * Override the boot-time initialization method here, so that
   * the core JMTk code doesn't need to know about the 
   * BootImageInterface type.
   */
  public static void initializeHeader(BootImageInterface bootImage, int ref,
                                      Object[] tib, int size, boolean isScalar)
    throws VM_PragmaInterruptible {
    //    int status = VM_JavaHeader.readAvailableBitsWord(bootImage, ref);
    VM_Word status = Plan.getBootTimeAvailableBits(ref, tib, size, VM_Word.zero());
    VM_JavaHeader.writeAvailableBitsWord(bootImage, ref, status);
  }

  public static void dumpHeader(Object ref) throws VM_PragmaUninterruptible {
    // currently unimplemented
    //    Header.dumpHeader(VM_Magic.objectAsAddress(ref));
  }

}
