/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Machine specific helper functions for dynamic linking.
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
class VM_DynamicLinkerHelper implements VM_Constants, Uninterruptible {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap 
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  static Object getReceiverObject() throws NoInlinePragma {

    Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    Address location = Address.zero();
    if (0 < NUM_PARAMETER_GPRS) {
      location = callingFrame.add(VM_BaselineConstants.STACKFRAME_FIRST_PARAMETER_OFFSET).loadAddress();

    } else {
      VM.sysFail("VM_DynamicLinerHelper: assumes at least one param passed in registers");
    }
    return VM_Magic.addressAsObject(location);
  }
}
