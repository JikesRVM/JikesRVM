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
package org.jikesrvm.ppc;

import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;

/**
 * Machine specific helper functions for dynamic linking.
 */
@Uninterruptible
public abstract class DynamicLinkerHelper implements RegisterConstants {

  /**
   * Reach up two stack frames into a frame that is compiled
   * with the DynamicBridge register protocol and grap
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  @NoInline
  public
  static Object getReceiverObject() {
    // reach into register save area and fetch "this" parameter
    Address callingFrame = Magic.getCallerFramePointer(Magic.getFramePointer());
    callingFrame = Magic.getCallerFramePointer(callingFrame);
    callingFrame = Magic.getCallerFramePointer(callingFrame);
    Address location =
        callingFrame.minus((LAST_NONVOLATILE_FPR - FIRST_VOLATILE_FPR + 1) * BYTES_IN_DOUBLE +
                           (LAST_NONVOLATILE_GPR - FIRST_VOLATILE_GPR + 1) * BYTES_IN_ADDRESS);

    return Magic.addressAsObject(location.loadAddress());
  }
}
