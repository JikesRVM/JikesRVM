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

import org.jikesrvm.VM;
import org.jikesrvm.architecture.MachineRegister;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.UninterruptibleNoWarn;

/**
 * Register Usage Conventions for ARM.
 */
public final class RegisterConstants {
  // Machine instructions.
  //

  /** log2 of instruction width in bytes, ARM */
  public static final int LG_INSTRUCTION_WIDTH = 2;
  /** instruction width in bytes, ARM */
  public static final int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH;

  /*
   * Representation of general purpose registers
   */
  public enum GPR implements MachineRegister {
    R0(0),   R1(1),   R2(2),   R3(3),   R4(4),   R5(5),   R6(6),   R7(7),   R8(8),   R9(9),
    R10(10), R11(11), R12(12), SP(13), LR(14), PC(15);

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
        VM._assert(result >= 0 && result <= 15);
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
   * Representation of floating point registers
   */
  public enum FPR implements MachineRegister  {
    S0(0),   S1(1),   S2(2),   S3(3),   S4(4),   S5(5),   S6(6),   S7(7),   S8(8),   S9(9),
    S10(10), S11(11), S12(12), S13(13), S14(14), S15(15), S16(16), S17(17), S18(18), S19(19),
    S20(20), S21(21), S22(22), S23(23), S24(24), S25(25), S26(26), S27(27), S28(28), S29(29),
    S30(30), S31(31);

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
    @Uninterruptible
    @Pure
    public static FPR lookup(int num) {
      return vals[num];
    }

    /** @return register next register to this one (e.g. F1 for F0) */
    @Uninterruptible
    @Pure
    public FPR nextFPR() {
      return lookup(value() + 1);
    }
  }

  /**
   * Representation of double precision floating point registers
   * The DX ones are not valid register values
   */
  public enum DPR implements MachineRegister  {
    D0(0),   DX0(1),   D1(2),   DX1(3),   D2(4),   DX2(5),   D3(6),   DX3(7),   D4(8),   DX4(9),
    D5(10), DX5(11), D6(12), DX6(13), D7(14), DX7(15), D8(16), DX8(17), D9(18), DX9(19),
    D10(20), DX10(21), D11(22), DX11(23), D12(24), DX12(25), D13(26), DX13(27), D14(28), DX14(29),
    D15(30), DX15(31);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final DPR[] vals = values();

    DPR(int v) {
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
        VM._assert(result >= 0 && result <= 30 && (result & 1) == 0);
      }
      return result;
    }

    /**
     * Converts encoded value into the DPR it represents
     * @param num encoded value
     * @return represented GPR
     */
    @Uninterruptible
    @Pure
    public static DPR lookup(int num) {
      if (VM.VerifyAssertions) VM._assert((num & 1) == 0);
      return vals[num];
    }

        /** @return register next register to this one (e.g. D1 for D0) */
    @Uninterruptible
    @Pure
    public DPR nextDPR() {
      if (VM.VerifyAssertions) VM._assert((value() & 1) == 0);
      return lookup(value() + 2);
    }
  }

  // OS register convention (for mapping parameters in JNI calls)
  // These constants encode conventions for Linux.

  // General purpose registers
  public static final GPR FIRST_OS_VOLATILE_GPR = GPR.R0;
  public static final GPR LAST_OS_VOLATILE_GPR = GPR.R3;
  public static final GPR FIRST_OS_PARAMETER_GPR = FIRST_OS_VOLATILE_GPR;
  public static final GPR LAST_OS_PARAMETER_GPR = LAST_OS_VOLATILE_GPR;
  public static final GPR FIRST_OS_NONVOLATILE_GPR = GPR.R4;
  public static final GPR LAST_OS_NONVOLATILE_GPR = GPR.R11;
  public static final int NUM_OS_PARAMETER_GPRS = LAST_OS_PARAMETER_GPR.value() - FIRST_OS_PARAMETER_GPR.value() + 1;

  public static final GPR SP = GPR.SP;
  public static final GPR LR = GPR.LR;
  public static final GPR PC = GPR.PC;

  // Floating point: single precision view
  public static final FPR FIRST_OS_VOLATILE_FPR = FPR.S0;
  public static final FPR LAST_OS_VOLATILE_FPR = FPR.S15;
  public static final FPR FIRST_OS_PARAMETER_FPR = FIRST_OS_VOLATILE_FPR;
  public static final FPR LAST_OS_PARAMETER_FPR = LAST_OS_VOLATILE_FPR;
  public static final FPR FIRST_OS_NONVOLATILE_FPR = FPR.S16;
  public static final FPR LAST_OS_NONVOLATILE_FPR = FPR.S31;
  public static final int NUM_OS_PARAMETER_FPRS = LAST_OS_PARAMETER_FPR.value() - FIRST_OS_PARAMETER_FPR.value() + 1;

  // Floating point: Double precision view
  public static final DPR FIRST_OS_VOLATILE_DPR = DPR.D0;
  public static final DPR LAST_OS_VOLATILE_DPR = DPR.D7;
  public static final DPR FIRST_OS_PARAMETER_DPR = FIRST_OS_VOLATILE_DPR;
  public static final DPR LAST_OS_PARAMETER_DPR = LAST_OS_VOLATILE_DPR;
  public static final DPR FIRST_OS_NONVOLATILE_DPR = DPR.D8;
  public static final DPR LAST_OS_NONVOLATILE_DPR = DPR.D15;
  public static final int NUM_OS_PARAMETER_DPRS = (LAST_OS_PARAMETER_DPR.value() / 2 - FIRST_OS_PARAMETER_DPR.value() / 2 + 1);

  // For double precision view, use the matching floating point register (D0 = F0, D1 = F2, D2 = F4 etc.)
  // Ignore optional (and volatile) D16-D31 registers

  // Jikes RVM's general purpose register usage (32 bits wide).
  public static final GPR FIRST_VOLATILE_GPR = FIRST_OS_VOLATILE_GPR;
  public static final GPR LAST_VOLATILE_GPR = LAST_OS_VOLATILE_GPR;
  public static final GPR FIRST_LOCAL_GPR = FIRST_OS_NONVOLATILE_GPR;
  public static final GPR LAST_LOCAL_GPR = GPR.R8;

  // NOTE: the ARM-specific part of the bootloader that deals with starting of threads
  // makes assumptions about the register usage. You will need to update the code
  // there if you change the assignments for the JTOC pointer or the thread register.

  public static final GPR TR = GPR.R9;
  public static final GPR JTOC = GPR.R10;
  public static final GPR FP = GPR.R11;
  public static final GPR R12 = GPR.R12;

  public static final GPR FIRST_NONVOLATILE_GPR = FIRST_OS_NONVOLATILE_GPR;
  public static final GPR LAST_NONVOLATILE_GPR = LAST_OS_NONVOLATILE_GPR;
  public static final int NUM_GPRS = 14; // All except the LR and PC (but includes the SP)
  public static final int NUM_VOLATILE_GPRS = LAST_VOLATILE_GPR.value() - FIRST_VOLATILE_GPR.value() + 1;
  public static final int NUM_NONVOLATILE_GPRS = LAST_NONVOLATILE_GPR.value() - FIRST_NONVOLATILE_GPR.value() + 1;

  // Floating point register usage
  public static final FPR FIRST_VOLATILE_FPR = FIRST_OS_VOLATILE_FPR;
  public static final FPR LAST_VOLATILE_FPR = LAST_OS_VOLATILE_FPR;
  public static final FPR FIRST_NONVOLATILE_FPR = FIRST_OS_NONVOLATILE_FPR;
  public static final FPR LAST_NONVOLATILE_FPR = LAST_OS_NONVOLATILE_FPR;
  public static final FPR FIRST_LOCAL_FPR = FIRST_NONVOLATILE_FPR;
  public static final FPR LAST_LOCAL_FPR = LAST_NONVOLATILE_FPR;
  public static final int NUM_FPRS = 32;
  public static final int NUM_VOLATILE_FPRS = LAST_VOLATILE_FPR.value() - FIRST_VOLATILE_FPR.value() + 1;
  public static final int NUM_NONVOLATILE_FPRS = LAST_NONVOLATILE_FPR.value() - FIRST_NONVOLATILE_FPR.value() + 1;

  public static final DPR FIRST_VOLATILE_DPR = FIRST_OS_VOLATILE_DPR;
  public static final DPR LAST_VOLATILE_DPR = LAST_OS_VOLATILE_DPR;
  public static final DPR FIRST_NONVOLATILE_DPR = FIRST_OS_NONVOLATILE_DPR;
  public static final DPR LAST_NONVOLATILE_DPR = LAST_OS_NONVOLATILE_DPR;
  public static final DPR FIRST_LOCAL_DPR = FIRST_NONVOLATILE_DPR;
  public static final DPR LAST_LOCAL_DPR = LAST_NONVOLATILE_DPR;
  public static final int NUM_DPRS = 16;
  public static final int NUM_VOLATILE_DPRS = LAST_VOLATILE_DPR.value() / 2 - FIRST_VOLATILE_DPR.value() / 2 + 1;
  public static final int NUM_NONVOLATILE_DPRS = LAST_NONVOLATILE_DPR.value() / 2 - FIRST_NONVOLATILE_DPR.value() / 2 + 1;

 /**
   * We rarely use the quad-word registers, just define this one
   */
  public enum QPR implements MachineRegister  {
    Q0;

    /** @return encoded value of this register */
    @Override
    @Pure
    public byte value() {
      return 0;
    }
  }

  private RegisterConstants() {
    // prevent instantiation
  }
}

