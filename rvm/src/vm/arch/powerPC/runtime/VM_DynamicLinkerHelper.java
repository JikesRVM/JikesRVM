/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Machine specific helper functions for dynamic linking.
 *
 * @author Bowen Alpern 
 * @author Derek Lieber
 * @date 17 Sep 1999  
 */
class VM_DynamicLinkerHelper implements VM_Constants, VM_Uninterruptible {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap 
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  static Object getReceiverObject() throws VM_PragmaNoInline {
    // reach into register save area and fetch "this" parameter
    VM_Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    VM_Address location = callingFrame.sub((LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * BYTES_IN_DOUBLE + 
                                           (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * BYTES_IN_ADDRESS); 
    
    return VM_Magic.addressAsObject(VM_Magic.getMemoryAddress(location));
  }
}
