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
package org.jikesrvm.jni.ppc;

import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_GC_FLAG_OFFSET;
import static org.jikesrvm.jni.ppc.JNIStackframeLayoutConstants.JNI_RVM_NONVOLATILE_OFFSET;
import static org.jikesrvm.ppc.RegisterConstants.FIRST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import org.jikesrvm.jni.AbstractJNIGCMapIterator;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

/**
 * Iterator for stack frames inserted at the transition from Java to
 * JNI Native C.  It will report JREFs associated with the executing
 * C frames which are in the "JREFs stack" attached to the executing
 * Threads JNIEnvironment.  It will update register location addresses
 * for the non-votatile registers to point to the register save area
 * in the transition frame.
 * <p>
 * If GC happens, the saved non-volatile regs may get modified (ex. a ref
 * to a live object that gets moved), and a restore flag in the frame is
 * set to cause the returning Native code to restore those registers from
 * this save area.  If GC does not occur, the Native C code has restored
 * these regs, and the transition return code does not do the restore.
 */
@Uninterruptible
public final class JNIGCMapIterator extends AbstractJNIGCMapIterator {

  // non-volitile regs are saved at the end of the transition frame,
  // after the saved JTOC and SP, and preceeded by a GC flag.
  //
  // JNI Java to Native C transition frame...
  //
  //     <-- | saved FP       |  <- this.framePtr
  //     |   |    ...         |
  //     |   |    ...         |
  //     |   | GC flag        |
  //     |   | saved affinity |
  //     |   | proc reg       |
  //     |   | non vol 17     |
  //     |   |    ...         |
  //     |   | non vol 31     |
  //     |   | saved SP       |
  //     |   | saved JTOC     |
  //     --> |                |  <- callers FP
  //
  // The following constant is the offset from the callers FP to
  // the GC flag at the beginning of this area.
  //

  public JNIGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  @Override
  protected void setupIteratorForArchitecture() {
    Address callers_fp = this.framePtr.loadAddress();

    // set the GC flag in the Java to C frame to indicate GC occurred
    // this forces saved non volatile regs to be restored from save area
    // where those containing refs have been relocated if necessary
    //
    callers_fp.minus(JNI_GC_FLAG_OFFSET).store(1);
  }

  /**
   * Sets register locations for non-volatiles to point to registers saved in
   * the JNI transition frame at a fixed negative offset from the callers FP.
   */
  @Override
  protected void setRegisterLocations() {
    Address registerLocation = this.framePtr.loadAddress().minus(JNI_RVM_NONVOLATILE_OFFSET);

    for (int i = LAST_NONVOLATILE_GPR.value(); i >= FIRST_NONVOLATILE_GPR.value() - 1; --i) {
      registerLocations.set(i, registerLocation);
      registerLocation = registerLocation.minus(BYTES_IN_ADDRESS);
    }
  }

  @Override
  public Address getNextReturnAddressAddress() {
    return Address.zero();
  }

}
