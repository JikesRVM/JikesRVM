/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.BootImageInterface;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Constants;
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
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
public class SimpleRCHeader extends SimpleRCBaseHeader implements VM_Constants {
  /**
   * Perform any required initialization of the GC portion of the header.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeader(Object ref, Object[] tib, int size,
				      boolean isScalar)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    // all objects are birthed with an RC of INCREMENT
    int initialValue = INCREMENT;
//     if (Plan.refCountCycleDetection && 
// 	VM_Magic.objectAsType(tib[TIB_TYPE_INDEX]).acyclic)
//       initialValue |= GREEN;
      
    VM_Magic.setIntAtOffset(ref, RC_HEADER_OFFSET, initialValue);
    int oldValue = VM_ObjectModel.readAvailableBitsWord(ref);
    int newValue = (oldValue & ~SMALL_OBJECT_MASK) | Plan.getInitialHeaderValue(size);
    VM_ObjectModel.writeAvailableBitsWord(ref, newValue);
  }
}
