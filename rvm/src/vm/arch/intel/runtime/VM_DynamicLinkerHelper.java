/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Machine specific helper functions for dynamic linking.
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
class VM_DynamicLinkerHelper implements VM_Constants, VM_Uninterruptible {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap 
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  static Object getReceiverObject() {
    VM_Magic.pragmaNoInline();

    int callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    int location = 0;
    if (0 < NUM_PARAMETER_GPRS) {
      location = VM_Magic.getMemoryWord(callingFrame + 
					VM_BaselineConstants.STACKFRAME_FIRST_PARAMETER_OFFSET);

    } else {
      VM.sysFail("VM_DynamicLinerHelper: assumes at least one param passed in registers");
    }
    return VM_Magic.addressAsObject(location);
  }
}
