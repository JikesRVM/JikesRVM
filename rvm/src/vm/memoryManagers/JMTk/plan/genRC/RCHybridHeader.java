/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
public class RCHybridHeader extends RCBaseHeader {
  public static final int GC_FORWARDED       = 0x2;  // ...10
  public static final int GC_BEING_FORWARDED = 0x3;  // ...11

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(VM_Address ref, Object[] tib, int size,
                                      boolean isScalar)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    // nothing here because this is for default allocation, which is
    // to the nursery, which requires nothing to be done.
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param isScalar are we initializing a scalar (true) or array
   * (false) object?
   * @param initialInc do we want to initialize this header with an
   * initial increment?
   */
  public static void initializeRCHeader(VM_Address ref, Object[] tib, int size,
                                        boolean isScalar, boolean initialInc)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int initialValue = (initialInc) ? INCREMENT : 0;
    if (Plan.REF_COUNT_CYCLE_DETECTION && VM_Interface.isAcyclic(tib))
      initialValue |= GREEN;
    VM_Magic.setIntAtOffset(ref, RC_HEADER_OFFSET, initialValue);
  }
}
