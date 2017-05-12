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
package org.jikesrvm.architecture;

import org.jikesrvm.VM;

import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public final class StackFrameLayout {

  public static int getNormalStackSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getMaxStackSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_MAX;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_MAX;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_MAX;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getBootThreadStackSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackSizeCollector() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_COLLECTOR;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_COLLECTOR;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_COLLECTOR;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackSizeGCDisabled() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_GCDISABLED;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GCDISABLED;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_GCDISABLED;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackSizeDLOpen() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_DLOPEN;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_DLOPEN;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_DLOPEN;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackSizeBoot() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_BOOT;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackSizeNormal() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_NORMAL;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getJNIStackGrowthSize() {
        if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_JNINATIVE_GROW;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_JNINATIVE_GROW;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_JNINATIVE_GROW;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackGrowthSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_GROW;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GROW;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_GROW;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackSizeGuard() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACK_SIZE_GUARD;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACK_SIZE_GUARD;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACK_SIZE_GUARD;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static Address getStackFrameSentinelFP() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return Address.zero();
    }
  }
  public static int getInvisibleMethodID() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.INVISIBLE_METHOD_ID;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static int getStackFrameHeaderSize() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_HEADER_SIZE;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return -1;
    }
  }
  public static Offset getStackFrameMethodIDOffset() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return Offset.zero();
    }
  }
  public static Offset getStackFramePointerOffset() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_FRAME_POINTER_OFFSET;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return Offset.zero();
    }
  }
  public static Offset getStackFrameReturnAddressOffset() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
    } else if (VM.BuildForPowerPC) {
      return org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
    } else if (VM.BuildForARM) {
      return org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED); return Offset.zero();
    }
  }

}
