/*
 * (C) Copyright IBM Corp. 2001
 */

package org.mmtk.utility.heap;

import org.mmtk.vm.Constants;
import org.mmtk.vm.VM_Interface;


import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
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

  /****************************************************************************
   *
   * Public instance methods
   */
  /**
   * Constructor
   */
  public ImmortalVMResource(byte space_, String vmName, MemoryResource mr, VM_Address vmStart, VM_Extent bytes) {
    super(space_, vmName, mr, vmStart, bytes, (byte) (VMResource.IN_VM | VMResource.IMMORTAL));
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(cursor.GE(vmStart) && cursor.LE(sentinel));
    sentinel = start.add(bytes);
  }

  public final VM_Address acquire(int pageRequest) {
    VM_Address result = super.acquire(pageRequest);
    acquireHelp(start, pageRequest);
    return result;
  }

  public final void release() {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }
  
}
