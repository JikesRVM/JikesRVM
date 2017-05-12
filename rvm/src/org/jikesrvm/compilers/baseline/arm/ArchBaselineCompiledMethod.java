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
package org.jikesrvm.compilers.baseline.arm;

import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.UnboxedSizeConstants.LOG_BYTES_IN_ADDRESS;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;
import static org.jikesrvm.compilers.baseline.arm.BaselineCompilerImpl.MAX_REGISTER_LOC;
import static org.jikesrvm.compilers.baseline.arm.BaselineCompilerImpl.isRegister;
import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_DPR;
import static org.jikesrvm.arm.RegisterConstants.FIRST_LOCAL_GPR;
import static org.jikesrvm.arm.RegisterConstants.LAST_LOCAL_GPR;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_SAVED_REGISTER_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_PARAMETER_OFFSET;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.BaselineCompiler;
import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.Address;
import org.vmmagic.pragma.Uninterruptible;

/**
 * Architecture-specific information about PPC baseline compiled methods.
 * Allows the location of registers, locals, and stack slots to be recovered
 */
public final class ArchBaselineCompiledMethod extends BaselineCompiledMethod {

  public ArchBaselineCompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  private short[] localGeneralLocations;
  private int SAVED_REGISTER_BYTES;
  private int LOCALS_BYTES;
  private short nextGPR;
  private short nextFPR;

  @Override
  protected void saveCompilerData(BaselineCompiler abstractComp) {
    BaselineCompilerImpl comp = (BaselineCompilerImpl) abstractComp;

    localGeneralLocations = comp.getlocalGeneralLocations();
    SAVED_REGISTER_BYTES = comp.getSavedRegisterBytes();
    LOCALS_BYTES = comp.getLocalsBytes();
    nextGPR = comp.getNextGPR();
    nextFPR = comp.getNextFPR();
  }

  @Uninterruptible
  Address getLocalAddressForGCMap(int index, AddressArray registerLocations, Address framePtr) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      return registerLocations.get(loc);
    } else if (loc > MAX_REGISTER_LOC) {
      return framePtr.plus(STACKFRAME_PARAMETER_OFFSET.toInt() + loc - MAX_REGISTER_LOC);
    } else {
      return framePtr.plus(STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + loc);
    }
  }

  @Uninterruptible
  Address getStackAddressForGCMap(int index, Address framePtr) {
    // Index + 1 because we want the address of the stack slot, not the top of it
    return framePtr.plus(getEmptyStackOffset()).minus((index + 1) << LOG_BYTES_IN_ADDRESS);
  }

  @Uninterruptible
  Address getLocalValueForExceptionDeliverer(int index, AbstractRegisters registers, Address framePtr) {
    short loc = localGeneralLocations[index];
    if (isRegister(loc)) {
      return registers.getGPRs().get(loc).toAddress();
    } else if (loc > MAX_REGISTER_LOC) {
      return framePtr.plus(STACKFRAME_PARAMETER_OFFSET.toInt() + loc - MAX_REGISTER_LOC).loadAddress();
    } else {
      return framePtr.plus(STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES + loc).loadAddress();
    }
  }

  @Uninterruptible
  void recordSavedRegisterLocations(AddressArray registerLocations, Address framePtr) {
    if (VM.VerifyAssertions) VM._assert(!((NormalMethod)getMethod()).getDeclaringClass().hasDynamicBridgeAnnotation());

    Address addr = framePtr.plus(STACKFRAME_SAVED_REGISTER_OFFSET);

    // Record GPR locs
    for (int i = nextGPR - 1; i >= FIRST_LOCAL_GPR.value(); i--) {
      addr = addr.minus(BYTES_IN_ADDRESS);
      registerLocations.set(i, addr);
    }

    // This is for the GC map: don't record FPRs
  }

  @Uninterruptible
  void recordSavedBridgeRegisterLocations(AddressArray registerLocations, Address framePtr) {
    if (VM.VerifyAssertions) VM._assert(((NormalMethod)getMethod()).getDeclaringClass().hasDynamicBridgeAnnotation());

    Address addr = framePtr.plus(STACKFRAME_SAVED_REGISTER_OFFSET);

    // Record local GPR locs
    for (int i = LAST_LOCAL_GPR.value(); i >= FIRST_LOCAL_GPR.value(); i--) {
      addr = addr.minus(BYTES_IN_ADDRESS);
      registerLocations.set(i, addr);
    }

    if (VM.VerifyAssertions) VM._assert(LAST_VOLATILE_GPR.value() < FIRST_LOCAL_GPR.value());

    // Record volatile GPR locs
    for (int i = LAST_VOLATILE_GPR.value(); i >= FIRST_VOLATILE_GPR.value(); i--) {
      addr = addr.minus(BYTES_IN_ADDRESS);
      registerLocations.set(i, addr);
    }

    // This is for the GC map: don't record FPRs
  }

  @Uninterruptible
  void writeSavedRegisterValues(AbstractRegisters registers, Address framePtr) {
    if (VM.VerifyAssertions) VM._assert(!((NormalMethod)getMethod()).getDeclaringClass().hasDynamicBridgeAnnotation());

    Address addr = framePtr.plus(STACKFRAME_SAVED_REGISTER_OFFSET);

    // Write GPRs
    for (int i = nextGPR - 1; i >= FIRST_LOCAL_GPR.value(); i--) {
      addr = addr.minus(BYTES_IN_ADDRESS);
      registers.getGPRs().set(i, addr.loadWord());
    }

    if (VM.VerifyAssertions) VM._assert((nextFPR & 1) == 0);

    // Write FPRs
    for (int i = nextFPR - 2; i >= FIRST_LOCAL_DPR.value(); i--) {
      addr = addr.minus(BYTES_IN_DOUBLE);
      registers.getFPRs()[i] = addr.loadDouble();
    }
  }

  // FP + getEmptyStackOffset() = value of SP when the operand stack is empty
  // Used for resetting the stack to deliver exceptions
  @Uninterruptible
  int getEmptyStackOffset() {
    return STACKFRAME_SAVED_REGISTER_OFFSET.toInt() - SAVED_REGISTER_BYTES - LOCALS_BYTES;
  }

}
