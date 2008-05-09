/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.ia32;

import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.UninterruptibleNoWarn;

public interface VM_RegisterConstants {
  //---------------------------------------------------------------------------------------//
  //               RVM register usage conventions - Intel version.                         //
  //---------------------------------------------------------------------------------------//

  byte LG_INSTRUCTION_WIDTH = 0;             // log2 of instruction width in bytes
  int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH;

  /**
   * Common interface implemented by all registers constants
   */
  public interface MachineRegister {
    /** @return encoded value of this register */
    byte value();
  }

  /**
   * Representation of general purpose registers
   */
  public enum GPR implements MachineRegister {
    EAX(0), ECX(1), EDX(2), EBX(3), ESP(4), EBP(5), ESI(6), EDI(7);

    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final GPR[] vals = values();

    /** Constructor a register with the given encoding value */
    private GPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @UninterruptibleNoWarn
    @Pure
    public byte value() {
      if (!org.jikesrvm.VM.runningVM) {
        return (byte)ordinal();
      } else {
        return (byte)java.lang.JikesRVMSupport.getEnumOrdinal(this);
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
     * @param num encoded value
     * @return represented GPR
     */
    public static GPR getForOpcode(int opcode) {
      return lookup(opcode);
    }
  }

  /**
   * Representation of x87 floating point registers
   */
  public enum FPR implements MachineRegister {
    FP0(0), FP1(1), FP2(2), FP3(3), FP4(4), FP5(5), FP6(6), FP7(7);
    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final FPR[] vals = values();
    /** Constructor a register with the given encoding value */
    FPR(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @Pure
    public byte value() {
      return (byte)ordinal();
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
    MM0(0), MM1(1), MM2(2), MM3(3), MM4(4), MM5(5), MM6(6), MM7(7);
    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final MM[] vals = values();
    /** Constructor a register with the given encoding value */
    MM(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @Pure
    public byte value() {
      return (byte)ordinal();
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
  public enum XMM implements MachineRegister {
    XMM0(0), XMM1(1), XMM2(2), XMM3(3), XMM4(4), XMM5(5), XMM6(6), XMM7(7);
    /** Local copy of the backing array. Copied here to avoid calls to clone */
    private static final XMM[] vals = values();
    /** Constructor a register with the given encoding value */
    XMM(int v) {
      if (v != ordinal()) {
        throw new Error("Invalid register ordinal");
      }
    }
    /** @return encoded value of this register */
    @Pure
    public byte value() {
      return (byte)ordinal();
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

  // Symbolic values for fixed-point registers.
  // These values are used to assemble instructions and as indices into:
  //   VM_Registers.gprs[]
  //   VM_Registers.fprs[]
  //   VM_GCMapIterator.registerLocations[]
  //   VM_RegisterConstants.GPR_NAMES[]
  GPR EAX = GPR.EAX;
  GPR ECX = GPR.ECX;
  GPR EDX = GPR.EDX;
  GPR EBX = GPR.EBX;
  GPR ESP = GPR.ESP;
  GPR EBP = GPR.EBP;
  GPR ESI = GPR.ESI;
  GPR EDI = GPR.EDI;

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

  XMM XMM0 = XMM.XMM0;
  XMM XMM1 = XMM.XMM1;
  XMM XMM2 = XMM.XMM2;
  XMM XMM3 = XMM.XMM3;
  XMM XMM4 = XMM.XMM4;
  XMM XMM5 = XMM.XMM5;
  XMM XMM6 = XMM.XMM6;
  XMM XMM7 = XMM.XMM7;

  // Register sets (``range'' is a misnomer for the alphabet soup of
  // of intel registers)
  //

  // Note: the order here is important.  The opt-compiler allocates
  // the volatile registers in the order they appear here.
  GPR[] VOLATILE_GPRS = {EAX, EDX, ECX};
  int NUM_VOLATILE_GPRS = VOLATILE_GPRS.length;

  // Note: the order here is very important.  The opt-compiler allocates
  // the nonvolatile registers in the reverse of order they appear here.
  // EBX must be last, because it is the only non-volatile that can
  // be used in instructions that are using r8 and we must ensure that
  // opt doesn't skip over another nonvol while looking for an r8 nonvol.
  GPR[] NONVOLATILE_GPRS = {EBP, EDI, EBX};
  int NUM_NONVOLATILE_GPRS = NONVOLATILE_GPRS.length;

  FPR[] VOLATILE_FPRS = {FP0, FP1, FP2, FP3, FP4, FP5, FP6, FP7};
  int NUM_VOLATILE_FPRS = VOLATILE_FPRS.length;

  FPR[] NONVOLATILE_FPRS = {};
  int NUM_NONVOLATILE_FPRS = NONVOLATILE_FPRS.length;

  /*
   * These constants represent the number of volatile registers used
   * to pass parameters in registers.  They are defined to mean that
   * the first n registers in the corresponding set of volatile
   * registers are used to pass parameters.
   */
  int NUM_PARAMETER_GPRS = 2;
  int NUM_PARAMETER_FPRS = 4;
  int NUM_RETURN_GPRS = 2;
  int NUM_RETURN_FPRS = 1;

  // Dedicated registers.
  //
  GPR STACK_POINTER = ESP;
  GPR PROCESSOR_REGISTER = ESI;

  byte NUM_GPRS = 8;
  byte NUM_FPRS = 8;
}
