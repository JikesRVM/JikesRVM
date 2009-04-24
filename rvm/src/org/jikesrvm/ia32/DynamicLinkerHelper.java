/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.ia32;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * Machine specific helper functions for dynamic linking.
 */
@Uninterruptible
public abstract class DynamicLinkerHelper {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  @NoInline
  public static Object getReceiverObject() {

    Address callingFrame = Magic.getCallerFramePointer(Magic.getFramePointer());
    callingFrame = Magic.getCallerFramePointer(callingFrame);
    Address location = Address.zero();
    if (0 < RegisterConstants.NUM_PARAMETER_GPRS) {
      location = callingFrame.plus(BaselineConstants.STACKFRAME_FIRST_PARAMETER_OFFSET).loadAddress();

    } else {
      VM.sysFail("DynamicLinerHelper: assumes at least one param passed in registers");
    }
    return Magic.addressAsObject(location);
  }
}
