/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;

import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * Defines header words used by memory manager.not used for 
 * XXX Line above incomplete
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
public class RCHeader extends RCBaseHeader {
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
    // all objects are birthed with an RC of INCREMENT
    int initialValue = INCREMENT;
    if (Plan.REF_COUNT_CYCLE_DETECTION && VM_Interface.isAcyclic(tib))
      initialValue |= GREEN;
    VM_Magic.setIntAtOffset(ref, RC_HEADER_OFFSET, initialValue);
  }

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects created at boot time.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for
   * this object.
   * @param isScalar are we initializing a scalar (true) or array
   * (false) object?
   */
  public static int getBootTimeAvailableBits(int ref, Object[] tib, int size,
                                             boolean isScalar, int status)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return status;  // do nothing
  }
}
