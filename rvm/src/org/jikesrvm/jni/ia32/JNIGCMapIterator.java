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
package org.jikesrvm.jni.ia32;

import static org.jikesrvm.ia32.RegisterConstants.EBP;
import static org.jikesrvm.ia32.RegisterConstants.EBX;
import static org.jikesrvm.ia32.RegisterConstants.EDI;
import org.jikesrvm.jni.AbstractJNIGCMapIterator;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;

/**
 * Iterator for stack frames inserted at the transition from Java to
 * JNI Native C.  It will report JREFs associated with the executing
 * C frames which are in the "JREFs stack" attached to the executing
 * Threads JNIEnvironment.  It will update register location addresses
 * for the non-volatile registers to point to the registers saved
 * in the transition frame.
 *
 * @see JNICompiler
 */
@Uninterruptible
public final class JNIGCMapIterator extends AbstractJNIGCMapIterator {

  // Java to Native C transition frame...(see JNICompiler)
  //
  //  0         + saved FP   + <---- FP for Java to Native C glue frame
  // -4         | methodID   |
  // -8         | saved EDI  |  non-volatile GPR
  // -C         | saved EBX  |  non-volatile GPR
  // -10        | saved EBP  |  non-volatile GPR
  // -14        | returnAddr |  (for return from OutOfLineMachineCode)
  // -18        | saved PR   |
  // -1C        | arg n-1    |  reordered arguments to native method
  // -20        |  ...       |  ...
  // -24        | arg 1      |  ...
  // -28        | arg 0      |  ...
  // -2C        | class/obj  |  required 2nd argument to all native methods
  // -30        | jniEnv     |  required 1st argument to all native methods
  // -34        | returnAddr |  return address pushed by call to native method
  //            + saved FP   +  <---- FP for called native method

  public JNIGCMapIterator(AddressArray registerLocations) {
    super(registerLocations);
  }

  @Override
  protected void setupIteratorForArchitecture() {
    // nothing to do for IA32
  }

  /**
   * Sets register locations for non-volatiles to point to registers saved in
   * the JNI transition frame at a fixed negative offset from the callers FP.
   * For IA32, the save non-volatiles are {@code EBX}, {@code EBP} and {@code EDI}.
   */
  @Override
  protected void setRegisterLocations() {
    registerLocations.set(EDI.value(), framePtr.plus(JNICompiler.EDI_SAVE_OFFSET));
    registerLocations.set(EBX.value(), framePtr.plus(JNICompiler.EBX_SAVE_OFFSET));
    registerLocations.set(EBP.value(), framePtr.plus(JNICompiler.EBP_SAVE_OFFSET));
  }

  @Override
  public Address getNextReturnAddressAddress() {
    return Address.zero();
  }

}
