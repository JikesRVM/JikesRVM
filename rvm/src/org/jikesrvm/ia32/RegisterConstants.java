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

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.UninterruptibleNoWarn;
import org.jikesrvm.VM;

public interface RegisterConstants {
  //---------------------------------------------------------------------------------------//
  //               RVM register usage conventions - Intel version.                         //
  //---------------------------------------------------------------------------------------//

  /** log2 of instruction width in bytes */
  byte LG_INSTRUCTION_WIDTH = 0;
  int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH;

  /**
   * Common interface implemented by all registers constants
   */
  public interface MachineRegister {
    /** @return encoded value of this register */
    byte value();
    /** @return does this register require a REX prefix byte? */
    boolean needsREXprefix();
  }

  /**
   * Super interface for floating point registers
   */
  public interface FloatingPointMachineRegister extends MachineRegister {
  }

  /**
   * Representation of general purpose registers
   */
  public enum GPR implements MachineRegister {
    EAX(0), ECX(1), EDX(2), EBX(3), ESP(4), EBP(5), ESI(6), EDI(7),
    R8(8), R9(9), R10(10), R11(11), R12(12), R13(13), R14(14), R15(15),
    EIP(16);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final GPR[] vals = values();

    /** Constructor a register with the given encoding value */
    private GPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    @Override
    @UninterruptibleNoWarn("Interruptible code only called during boot image creation")
    @Pure
    public byte value() {
      byte result;
      if (!org.jikesrvm.VM.runningVM) {
        result = (byte)ordinal();
      } else {
        result = (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
      }
      if (VM.VerifyAssertions) {
        if (VM.buildFor32Addr()) {
          VM._assert(result >=0 && result <= 7);
        } else {
          VM._assert(result >=0 && result <= 15);
        }
      }
      return result;
    }
    /** @return encoded value of this register to be included in the opcode byte */
    @Pure
    public byte valueForOpcode() {
      byte result;
      if (!org.jikesrvm.VM.runningVM) {
        result = (byte)ordinal();
      } else {
        result = (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
      }
      if (!VM.buildFor32Addr()) {
        result &= 0x7;
      }
      if (VM.VerifyAssertions) {
        VM._assert(result >=0 && result <= 7);
      }
      return result;
    }
    @Override
    @Pure
    public boolean needsREXprefix() {
      if (VM.buildFor32Addr()) {
        return false;
      } else {
        return (this != EIP) && (value() > 7);
      }
    }
    /**
     * Convert encoded value into the GPR it represents
     * @param num encoded value
     * @return represented GPR
     */
    @Pure
    public static GPR lookup(int num) {
      return vals[num];
    }
    /**
     * Convert encoded value representing an opcode into the GPR to represent it
     * @param opcode encoded value
     * @return represented GPR
     */
    public static GPR getForOpcode(int opcode) {
      if (VM.VerifyAssertions) VM._assert(opcode >=0 && opcode <= 7);
      return lookup(opcode);
    }
  }

  /**
   * Representation of x87 floating point registers
   */
  public enum FPR implements FloatingPointMachineRegister {
    FP0(0), FP1(1), FP2(2), FP3(3), FP4(4), FP5(5), FP6(6), FP7(7);
    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final FPR[] vals = values();
    /** Constructor a register with the given encoding value */
    FPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    @Override
    @Pure
    public byte value() {
      return (byte)ordinal();
    }
    @Override
    @Pure
    public boolean needsREXprefix() {
      return false; // do REX prefixes of floating point operands make sense?
    }
    /**
     * Convert encoded value into the FPR it represents
     * @param num encoded value
     * @return represented FPR
     */
    @Pure
    public static FPR lookup(int num) {
      return vals[num];
    }
  }
  /**
   * Representation of MMX MM registers
   * N.B. MM and x87 FPR registers alias
   */
  public enum MM implements MachineRegister {
    MM0(0), MM1(1), MM2(2), MM3(3), MM4(4), MM5(5), MM6(6), MM7(7),
    MM8(8), MM9(9), MM10(10), MM11(11), MM12(12), MM13(13), MM14(14), MM15(15);
    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final MM[] vals = values();
    /** Constructor a register with the given encoding value */
    MM(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    @Override
    @Pure
    public byte value() {
      return (byte)ordinal();
    }
    @Override
    @Pure
    public boolean needsREXprefix() {
      if (VM.buildFor32Addr()) {
        return false;
      } else {
        return value() > 7;
      }
    }
    /**
     * Convert encoded value into the MM it represents
     * @param num encoded value
     * @return represented MM
     */
    @Pure
    public static MM lookup(int num) {
      return vals[num];
    }
  }

  /**
   * Representation of SSE XMM registers
   */
  public enum XMM implements FloatingPointMachineRegister {
    XMM0(0), XMM1(1), XMM2(2), XMM3(3), XMM4(4), XMM5(5), XMM6(6), XMM7(7),
    XMM8(8), XMM9(9), XMM10(10), XMM11(11), XMM12(12), XMM13(13), XMM14(14), XMM15(15);
    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final XMM[] vals = values();
    /** Constructor a register with the given encoding value */
    XMM(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    @Override
    @Pure
    public byte value() {
      return (byte)ordinal();
    }
    @Override
    @Pure
    public boolean needsREXprefix() {
      if (VM.buildFor32Addr()) {
        return false;
      } else {
        return value() > 7;
      }
    }
    /**
     * Convert encoded value into the XMM it represents
     * @param num encoded value
     * @return represented XMM
     */
    @Pure
    public static XMM lookup(int num) {
      return vals[num];
    }
  }

  /*
   * Symbolic values for general purpose registers.
   * These values are used to assemble instructions and as indices into:
   *   Registers.gprs[]
   *   Registers.fprs[]
   *   GCMapIterator.registerLocations[]
   *   RegisterConstants.GPR_NAMES[]
   */
  GPR EAX = GPR.EAX;
  GPR ECX = GPR.ECX;
  GPR EDX = GPR.EDX;
  GPR EBX = GPR.EBX;
  GPR ESP = GPR.ESP;
  GPR EBP = GPR.EBP;
  GPR ESI = GPR.ESI;
  GPR EDI = GPR.EDI;

  GPR R0 = GPR.EAX;
  GPR R1 = GPR.ECX;
  GPR R2 = GPR.EDX;
  GPR R3 = GPR.EBX;
  GPR R4 = GPR.ESP;
  GPR R5 = GPR.EBP;
  GPR R6 = GPR.ESI;
  GPR R7 = GPR.EDI;
  GPR R8 = GPR.R8;
  GPR R9 = GPR.R9;
  GPR R10 = GPR.R10;
  GPR R11 = GPR.R11;
  GPR R12 = GPR.R12;
  GPR R13 = GPR.R13;
  GPR R14 = GPR.R14;
  GPR R15 = GPR.R15;

  FPR FP0 = FPR.FP0;
  FPR FP1 = FPR.FP1;
  FPR FP2 = FPR.FP2;
  FPR FP3 = FPR.FP3;
  FPR FP4 = FPR.FP4;
  FPR FP5 = FPR.FP5;
  FPR FP6 = FPR.FP6;
  FPR FP7 = FPR.FP7;

  MM MM0 = MM.MM0;
  MM MM1 = MM.MM1;
  MM MM2 = MM.MM2;
  MM MM3 = MM.MM3;
  MM MM4 = MM.MM4;
  MM MM5 = MM.MM5;
  MM MM6 = MM.MM6;
  MM MM7 = MM.MM7;
  MM MM8 = MM.MM8;
  MM MM9 = MM.MM9;
  MM MM10 = MM.MM10;
  MM MM11 = MM.MM11;
  MM MM12 = MM.MM12;
  MM MM13 = MM.MM13;
  MM MM14 = MM.MM14;
  MM MM15 = MM.MM15;

  XMM XMM0 = XMM.XMM0;
  XMM XMM1 = XMM.XMM1;
  XMM XMM2 = XMM.XMM2;
  XMM XMM3 = XMM.XMM3;
  XMM XMM4 = XMM.XMM4;
  XMM XMM5 = XMM.XMM5;
  XMM XMM6 = XMM.XMM6;
  XMM XMM7 = XMM.XMM7;
  XMM XMM8 = XMM.XMM8;
  XMM XMM9 = XMM.XMM9;
  XMM XMM10 = XMM.XMM10;
  XMM XMM11 = XMM.XMM11;
  XMM XMM12 = XMM.XMM12;
  XMM XMM13 = XMM.XMM13;
  XMM XMM14 = XMM.XMM14;
  XMM XMM15 = XMM.XMM15;

  /*
   * Dedicated registers.
   */

  /** Register current stack pointer. NB the frame pointer is maintained in the processor. */
  GPR STACK_POINTER = ESP;
  /** Register holding a reference to thread local information */
  GPR THREAD_REGISTER = ESI;

  /*
   * Register sets
   * (``range'' is a misnomer for the alphabet soup of of intel registers)
   */
// CHECKSTYLE:OFF
  /** All general purpose registers */
  GPR[] ALL_GPRS =
    VM.buildFor32Addr() ? new GPR[]{EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI}
                        : new GPR[]{EAX, ECX, EDX, EBX, ESP, EBP, ESI, EDI, R8, R9, R10, R11, R12, R13, R14, R15};

  /** Number of general purpose registers */
  byte NUM_GPRS = (byte)ALL_GPRS.length;

  /**
   * All floating point purpose registers
   * NB with SSE x87 registers must be explicitly managed
   */
  FloatingPointMachineRegister[] ALL_FPRS =
    VM.buildFor32Addr() ? (VM.buildForSSE2() ? new FPR[]{FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7}
                                             : new XMM[]{XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7})
      : new XMM[]{XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7,
                  XMM8, XMM9, XMM10, XMM11, XMM12, XMM13, XMM14, XMM15};

  /** Number of floating point registers */
  byte NUM_FPRS = (byte)ALL_FPRS.length;

  /**
   * Volatile general purpose registers.
   * NB: the order here is important.  The opt-compiler allocates
   * the volatile registers in the order they appear here.
   */
  GPR[] VOLATILE_GPRS = VM.buildFor32Addr() ? new GPR[]{R0 /*EAX*/, R2 /*EDX*/, R1 /*ECX*/} : new GPR[]{R0, R2, R1};
  int NUM_VOLATILE_GPRS = VOLATILE_GPRS.length;

  /**
   * Volatile floating point registers within the RVM.
   * TODO: this should include XMMs
   */
  FloatingPointMachineRegister[] VOLATILE_FPRS = {FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7};
  /** Number of volatile FPRs */
  int NUM_VOLATILE_FPRS = VOLATILE_FPRS.length;

  /**
   * Non-volatile general purpose registers within the RVM.
   * Note: the order here is very important.  The opt-compiler allocates
   * the nonvolatile registers in the reverse of order they appear here.
   * R3 (EBX) must be last, because it is the only non-volatile that can
   * be used in instructions that are using r8 and we must ensure that
   * opt doesn't skip over another nonvol while looking for an r8 nonvol.
   */
  GPR[] NONVOLATILE_GPRS =
    VM.buildFor32Addr() ? new GPR[]{R5 /*EBP*/, R7 /*EDI*/, R3 /*EBX*/}
                        : new GPR[]{R5, R7, R3};
  /** Number of non-volatile GPRs */
  int NUM_NONVOLATILE_GPRS = NONVOLATILE_GPRS.length;

  /** Non-volatile floating point registers within the RVM. */
  FloatingPointMachineRegister[] NONVOLATILE_FPRS = {};
  /** Number of non-volatile FPRs */
  int NUM_NONVOLATILE_FPRS = NONVOLATILE_FPRS.length;

  /** General purpose registers to pass arguments within the RVM */
  GPR[] PARAMETER_GPRS = new GPR[]{EAX, EDX};
  /** Number of parameter GPRs */
  int NUM_PARAMETER_GPRS = PARAMETER_GPRS.length;

  /** Floating point registers to pass arguments within the RVM */
  FloatingPointMachineRegister[] PARAMETER_FPRS =
    VM.buildForSSE2() ? new XMM[]{XMM0, XMM1, XMM2, XMM3}
                    : new FPR[]{FP0, FP1, FP2, FP3};
  /** Number of parameter FPRs */
  int NUM_PARAMETER_FPRS = PARAMETER_FPRS.length;
  /** GPR registers used for returning values */
  GPR[] RETURN_GPRS = VM.buildFor32Addr() ? new GPR[]{EAX, EDX} : new GPR[]{EAX};
  /** Number of return GPRs */
  int NUM_RETURN_GPRS = RETURN_GPRS.length;

  /** FPR registers used for returning values */
  FloatingPointMachineRegister[] RETURN_FPRS =
    VM.buildForSSE2() ? new XMM[]{XMM0} : new FPR[]{FP0};
  /** Number of return FPRs */
  int NUM_RETURN_FPRS = RETURN_FPRS.length;

  /** Native volatile GPRS */
  GPR[] NATIVE_VOLATILE_GPRS = VM.buildFor32Addr() ? new GPR[]{EAX, ECX, EDX} : new GPR[]{EAX, ECX, EDX, R8, R9, R10, R11};
  /** Native non-volatile GPRS */
  GPR[] NATIVE_NONVOLATILE_GPRS = VM.buildFor32Addr() ? new GPR[]{EBX, EBP, EDI, ESI} : new GPR[]{EBX, EBP, R12, R13, R14, R15};

  /** Native volatile FPRS */
  FloatingPointMachineRegister[] NATIVE_VOLATILE_FPRS = ALL_FPRS;
  /** Native non-volatile FPRS */
  FloatingPointMachineRegister[] NATIVE_NONVOLATILE_FPRS = new FloatingPointMachineRegister[0];

  /** General purpose registers to pass arguments to native code */
  GPR[] NATIVE_PARAMETER_GPRS =
    VM.buildFor32Addr() ? new GPR[0]
                        : new GPR[]{EDI /*R7*/, ESI /*R6*/, EDX /*R2*/, ECX /*R1*/, R8, R9};

  /** Floating point registers to pass arguments to native code */
  FloatingPointMachineRegister[] NATIVE_PARAMETER_FPRS =
    VM.buildFor32Addr() ? new FloatingPointMachineRegister[0]
                        : new XMM[]{XMM0, XMM1, XMM2, XMM3, XMM4, XMM5, XMM6, XMM7};
// CHECKSTYLE:ON
}
