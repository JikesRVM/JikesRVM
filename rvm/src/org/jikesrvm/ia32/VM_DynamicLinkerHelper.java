/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.VM_Magic;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * Machine specific helper functions for dynamic linking.
 * 
 * @author Bowen Alpern
 * @author Maria Butrico
 * @author Anthony Cocchi
 */
@Uninterruptible
public abstract class VM_DynamicLinkerHelper {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap 
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  @NoInline
  public
  static Object getReceiverObject() { 

    Address callingFrame = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());
    callingFrame = VM_Magic.getCallerFramePointer(callingFrame);
    Address location = Address.zero();
    if (0 < VM_RegisterConstants.NUM_PARAMETER_GPRS) {
      location = callingFrame.plus(VM_BaselineConstants.STACKFRAME_FIRST_PARAMETER_OFFSET).loadAddress();

    } else {
      VM.sysFail("VM_DynamicLinerHelper: assumes at least one param passed in registers");
    }
    return VM_Magic.addressAsObject(location);
  }
}
