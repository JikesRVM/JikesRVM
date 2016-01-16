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

import org.jikesrvm.VM;
import org.jikesrvm.architecture.MachineRegister;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * Register Usage Conventions for PowerPC.
 */
public final class RegisterConstants {
  // Machine instructions.
  //

  /** log2 of instruction width in bytes, powerPC */
  public static final int LG_INSTRUCTION_WIDTH = 2;
  /** instruction width in bytes, powerPC */
  public static final int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH;

  /**
   * Representation of general purpose registers
   */
  public enum GPR implements MachineRegister {
    R0(0),   R1(1),   R2(2),   R3(3),   R4(4),   R5(5),   R6(6),   R7(7),   R8(8),   R9(9),
    R10(10), R11(11), R12(12), R13(13), R14(14), R15(15), R16(16), R17(17), R18(18), R19(19),
    R20(20), R21(21), R22(22), R23(23), R24(24), R25(25), R26(26), R27(27), R28(28), R29(29),
    R30(30), R31(31);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final GPR[] vals = values();

    GPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }

    /** @return encoded value of this register */
    @UninterruptibleNoWarn("Interruptible code only called during boot image creation")
    @Pure
    @Override
    public byte value() {
      byte result;
      if (!org.jikesrvm.VM.runningVM) {
        result = (byte)ordinal();
      } else {
        result = (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
      }
      if (VM.VerifyAssertions) {
        VM._assert(result >= 0 && result <= 31);
      }
      return result;
    }

    /**
     * Converts encoded value into the GPR it represents
     * @param num encoded value
     * @return represented GPR
     */
    @Uninterruptible
    @Pure
    public static GPR lookup(int num) {
      return vals[num];
    }

    /** @return register next register to this one (e.g. R1 for R0) */
    @Uninterruptible
    @Pure
    public GPR nextGPR() {
      return lookup(value() + 1);
    }
  }

  /**
   * Super interface for floating point registers
   */
  public interface FloatingPointMachineRegister extends MachineRegister {
  }

  /**
   * Representation of floating point registers
   */
  public enum FPR implements FloatingPointMachineRegister {
    FR0(0),   FR1(1),   FR2(2),   FR3(3),   FR4(4),   FR5(5),   FR6(6),   FR7(7),   FR8(8),   FR9(9),
    FR10(10), FR11(11), FR12(12), FR13(13), FR14(14), FR15(15), FR16(16), FR17(17), FR18(18), FR19(19),
    FR20(20), FR21(21), FR22(22), FR23(23), FR24(24), FR25(25), FR26(26), FR27(27), FR28(28), FR29(29),
    FR30(30), FR31(31);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final FPR[] vals = values();

    FPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }

    /** @return encoded value of this register */
    @UninterruptibleNoWarn("Interruptible code only called during boot image creation")
    @Override
    @Pure
    public byte value() {
      byte result;
      if (!org.jikesrvm.VM.runningVM) {
        result = (byte)ordinal();
      } else {
        result = (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
      }
      if (VM.VerifyAssertions) {
        VM._assert(result >= 0 && result <= 31);
      }
      return result;
    }

    /**
     * Converts encoded value into the FPR it represents
     * @param num encoded value
     * @return represented GPR
     */
    @Pure
    public static FPR lookup(int num) {
      return vals[num];
    }
  }

  /**
   * Representation of condition registers
   */
  public enum CR implements MachineRegister {
    CR0(0),   CR1(1),   CR2(2),   CR3(3),   CR4(4),   CR5(5),   CR6(6),   CR7(7);

    CR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }

    /** @return encoded value of this register */
    @Override
    @Pure
    public byte value() {
      return (byte)ordinal();
    }
  }

  // OS register convention (for mapping parameters in JNI calls)
  // These constants encode conventions for Linux.

  // 0 is for function prologs, 1 is stack frame pointer, 2 is TOC pointer
  public static final GPR FIRST_OS_PARAMETER_GPR   = GPR.R3;
  public static final GPR LAST_OS_PARAMETER_GPR    = GPR.R10;
  public static final GPR FIRST_OS_VOLATILE_GPR    = GPR.R3;
  public static final GPR LAST_OS_VOLATILE_GPR     = GPR.R12;
  public static final GPR FIRST_OS_NONVOLATILE_GPR = (VM.BuildForPower64ELF_ABI) ? GPR.R14 : GPR.R13;
  public static final GPR LAST_OS_NONVOLATILE_GPR  = GPR.R31;
  public static final FPR FIRST_OS_PARAMETER_FPR   = FPR.FR1;
  public static final FPR LAST_OS_PARAMETER_FPR    = VM.BuildForLinux ? FPR.FR8 : FPR.FR13;
  public static final FPR FIRST_OS_VOLATILE_FPR    = FPR.FR1;
  public static final FPR LAST_OS_VOLATILE_FPR     = FPR.FR13;
  public static final FPR FIRST_OS_NONVOLATILE_FPR = FPR.FR14;
  public static final FPR LAST_OS_NONVOLATILE_FPR  = FPR.FR31;
  public static final FPR LAST_OS_VARARG_PARAMETER_FPR = FPR.FR8;

  // Jikes RVM's general purpose register usage (32 or 64 bits wide based on VM.BuildFor64Addr).
  //
  /** special instruction semantics on this register */
  public static final GPR REGISTER_ZERO = GPR.R0;
  /** same as Linux */
  public static final GPR FRAME_POINTER = GPR.R1;
  public static final GPR FIRST_VOLATILE_GPR = FIRST_OS_PARAMETER_GPR;
  //                                            ...
  public static final GPR LAST_VOLATILE_GPR = LAST_OS_PARAMETER_GPR;
  public static final GPR FIRST_SCRATCH_GPR = GPR.lookup(LAST_VOLATILE_GPR.value() + 1);
  public static final GPR LAST_SCRATCH_GPR = LAST_OS_VOLATILE_GPR;

  // NOTE: the PPC-specific part of the bootloader that deals with starting of threads
  // makes assumptions about the register usage. You will need to update the code
  // there if you change the assignments for the JTOC pointer or the thread register.

  // PowerPC 64 ELF ABI reserves R13 for use by libpthread; therefore Jikes RVM doesn't touch it.
  public static final GPR FIRST_RVM_RESERVED_NV_GPR = VM.BuildFor64Addr ? GPR.R14 : GPR.R13;
  public static final GPR THREAD_REGISTER = FIRST_RVM_RESERVED_NV_GPR;

  // 2 is used by Linux for thread context and on OS X it's a scratch.
  public static final GPR JTOC_POINTER = (VM.BuildForLinux && VM.BuildFor32Addr) ? GPR.lookup(THREAD_REGISTER.value() + 1) :
    GPR.R2; // use TOC register on PowerPC 64-bit ELF ABI
  // Use THREAD_REGISTER + 2 for 32-bit Linux and THREAD_REGISTER + 1 for 64-bit Linux
  public static final GPR KLUDGE_TI_REG = GPR.lookup(THREAD_REGISTER.value() + (VM.BuildForLinux && VM.BuildFor32Addr ? 2 : 1));

  public static final GPR LAST_RVM_RESERVED_NV_GPR = KLUDGE_TI_REG;
  public static final GPR FIRST_NONVOLATILE_GPR = GPR.values()[LAST_RVM_RESERVED_NV_GPR.value() + 1];
  //                                            ...
  public static final GPR LAST_NONVOLATILE_GPR = LAST_OS_NONVOLATILE_GPR;
  public static final int NUM_GPRS = 32;

  // Floating point register usage. (FPR's are 64 bits wide).

  /** Linux is 0 */
  public static final FPR FIRST_SCRATCH_FPR = FPR.FR0;
  /** Linux is 0 */
  public static final FPR LAST_SCRATCH_FPR = FPR.FR0;
  public static final FPR FIRST_VOLATILE_FPR = FIRST_OS_VOLATILE_FPR;
  //
  public static final FPR LAST_VOLATILE_FPR = LAST_OS_VOLATILE_FPR;
  public static final FPR FIRST_NONVOLATILE_FPR = FIRST_OS_NONVOLATILE_FPR;
  //                                            ...
  public static final FPR LAST_NONVOLATILE_FPR = LAST_OS_NONVOLATILE_FPR;
  public static final int NUM_FPRS = 32;

  public static final int NUM_NONVOLATILE_GPRS = LAST_NONVOLATILE_GPR.value() - FIRST_NONVOLATILE_GPR.value() + 1;
  public static final int NUM_NONVOLATILE_FPRS = LAST_NONVOLATILE_FPR.value() - FIRST_NONVOLATILE_FPR.value() + 1;

  // condition registers
  // TODO: fill table
  public static final int NUM_CRS = 8;

  /** number of special registers (user visible) */
  public static final int NUM_SPECIALS = 8;

  private RegisterConstants() {
    // prevent instantiation
  }

}

