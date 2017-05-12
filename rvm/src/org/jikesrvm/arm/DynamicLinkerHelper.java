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
package org.jikesrvm.arm;

import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_LOCAL_GPR;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_SAVED_REGISTER_OFFSET;

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
   * with the DynamicBridge register protocol and grab
   * the receiver object of the invoke (ie the first param).
   * NOTE: assumes that caller has disabled GC.
   */
  @NoInline
  public
  static Object getReceiverObject() {
    // reach into register save area and fetch "this" parameter
    Address addr = Magic.getCallerFramePointer(Magic.getFramePointer());
    addr = Magic.getCallerFramePointer(addr);

    // See BaselineCompilerImpl.genPrologue() for the locations of saved registers in a dynamic bridge method
    addr = addr.plus(STACKFRAME_SAVED_REGISTER_OFFSET);
    addr = addr.minus((LAST_LOCAL_GPR.value() - FIRST_LOCAL_GPR.value() + 1) * BYTES_IN_ADDRESS);
    addr = addr.minus((LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1) * BYTES_IN_ADDRESS);
    return Magic.addressAsObject(addr.loadAddress());
  }
}
