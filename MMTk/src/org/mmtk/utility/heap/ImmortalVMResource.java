/*
 * (C) Copyright IBM Corp. 2001
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * This class restricts MonotoneVMResource by preventing release of immortal memory.
 * There is functionality for boot-image support.
 *
 * 
 * @author Perry Cheng
 * @version $Revision$
 * @date $Date$
 */
public class ImmortalVMResource extends MonotoneVMResource implements Constants, VM_Uninterruptible {
  public final static String Id = "$Id$"; 

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  /**
   * Constructor
   */
  ImmortalVMResource(String vmName, MemoryResource mr, VM_Address vmStart, EXTENT bytes, VM_Address cursorStart) {
    super(vmName, mr, vmStart, bytes, (byte) (VMResource.IN_VM | VMResource.IMMORTAL));
    cursor = cursorStart;
    if (VM.VerifyAssertions) VM._assert(cursor.GE(vmStart) && cursor.LE(sentinel));
    sentinel = start.add(bytes);
  }

  public final VM_Address acquire(int blockRequest) {
    VM_Address result = super.acquire(blockRequest);
    if (VM.VerifyAssertions) VM._assert(!result.isZero());
    return result;
  }

  public final void release() {
    if (VM.VerifyAssertions) VM._assert(false);
  }
  
}
