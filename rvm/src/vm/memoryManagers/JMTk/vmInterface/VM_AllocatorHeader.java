/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.jikesrvm.memorymanagers.mmInterface;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

import com.ibm.jikesrvm.VM_Constants;
import com.ibm.jikesrvm.VM_Magic;
import com.ibm.jikesrvm.VM_JavaHeader;
import com.ibm.jikesrvm.BootImageInterface;

/**
 * Chooses the appropriate collector-specific header model.
 *
 * @see com.ibm.jikesrvm.VM_ObjectModel
 * 
 * @author Perry Cheng
 */
public final class VM_AllocatorHeader implements VM_Constants {
  public static final boolean STEAL_NURSERY_GC_HEADER = false;
  // not supported during expected transition to new object model.
  public static final boolean STEAL_NURSERY_SCALAR_GC_HEADER = false;
  public static final boolean NEEDS_LINEAR_SCAN = SelectedPlanConstraints.get().needsLinearScan();

  public static final int REQUESTED_BITS = SelectedPlanConstraints.get().gcHeaderBits();
  public static final int NUM_BYTES_HEADER = SelectedPlanConstraints.get().gcHeaderWords() << LOG_BYTES_IN_WORD;

  /**
   * Override the boot-time initialization method here, so that
   * the core JMTk code doesn't need to know about the 
   * BootImageInterface type.
   */
  public static void initializeHeader(BootImageInterface bootImage, Address ref,
                                      Object[] tib, int size, boolean isScalar)
    throws InterruptiblePragma {
    //    int status = VM_JavaHeader.readAvailableBitsWord(bootImage, ref);
    Word status = SelectedPlan.get().setBootTimeGCBits(ref, 
      ObjectReference.fromObject(tib), size, Word.zero());
    VM_JavaHeader.writeAvailableBitsWord(bootImage, ref, status);
  }

  public static void dumpHeader(Object ref) throws UninterruptiblePragma {
    // currently unimplemented
    //    Header.dumpHeader(VM_Magic.objectAsAddress(ref));
  }

}
