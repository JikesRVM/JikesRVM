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
package org.jikesrvm.compilers.opt.ir.ia32;

import java.util.Enumeration;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Operator;
import org.jikesrvm.compilers.opt.ir.Register;
import org.vmmagic.pragma.Pure;

/**
 * This class provides utilities to record defs and uses of physical
 * registers by IR operators.
 */
public abstract class PhysicalDefUse {

  // constants used to encode defs/uses of physical registers
  /** Default empty mask */
  public static final int mask = 0x0000;
  /** AF in the eflags is used/defined */
  public static final int maskAF = 0x0001;
  /** CF in the eflags is used/defined */
  public static final int maskCF = 0x0002;
  /** OF in the eflags is used/defined */
  public static final int maskOF = 0x0004;
  /** PF in the eflags is used/defined */
  public static final int maskPF = 0x0008;
  /** SF in the eflags is used/defined */
  public static final int maskSF = 0x0010;
  /** ZF in the eflags is used/defined */
  public static final int maskZF = 0x0020;
  /** C0 in the x87 FPU is used/defined */
  public static final int maskC0 = 0x0040;
  /** C1 in the x87 FPU is used/defined */
  public static final int maskC1 = 0x0080;
  /** C2 in the x87 FPU is used/defined */
  public static final int maskC2 = 0x0100;
  /** C3 in the x87 FPU is used/defined */
  public static final int maskC3 = 0x0200;
  /** The processor register is used/defined */
  public static final int maskTR = 0x0400;
  /** The ESP register is used/defined */
  public static final int maskESP= 0x0800;
  /* Meta mask for the enumeration. */
  /** First mask bit */
  private static final int maskHIGH = 0x0800;
  /** Mask for all bits */
  private static final int maskALL = 0x0FFF;

  public static final int maskCF_OF = maskCF | maskOF;
  public static final int maskCF_PF_ZF = maskCF | maskPF | maskZF;
  public static final int maskCF_OF_PF_SF_ZF = maskCF | maskOF | maskPF | maskSF | maskZF;
  public static final int maskAF_OF_PF_SF_ZF = maskAF | maskOF | maskPF | maskSF | maskZF;
  public static final int maskAF_CF_OF_PF_SF_ZF = maskAF | maskCF | maskOF | maskPF | maskSF | maskZF;
  public static final int maskC0_C1_C2_C3 = maskC0 | maskC1 | maskC2 | maskC3;
  public static final int maskcallDefs = maskAF_CF_OF_PF_SF_ZF | maskESP;
  public static final int maskcallUses = maskESP;
  public static final int maskIEEEMagicUses = mask;
  /** Uses mask used by dependence graph to show a yield point */
  public static final int maskTSPUses = maskESP;
  /** Definitions mask used by dependence graph to show a yield point */
  public static final int maskTSPDefs = maskAF_CF_OF_PF_SF_ZF | maskTR | maskESP;

  /**
   * @return whether or not an Operator uses the EFLAGS
   */
  public static boolean usesEFLAGS(Operator op) {
    return (op.implicitUses & maskAF_CF_OF_PF_SF_ZF) != 0;
  }

  /**
   * @return whether or not an Operator uses the EFLAGS
   */
  public static boolean definesEFLAGS(Operator op) {
    return (op.implicitDefs & maskAF_CF_OF_PF_SF_ZF) != 0;
  }

  /**
   * @return whether or not an Operator implicitly uses or defines ESP
   */
  public static boolean usesOrDefinesESP(Operator op) {
    return ((op.implicitUses & maskESP) != 0) || ((op.implicitDefs & maskESP) != 0);
  }
  /**
   * @return a string representation of the physical registers encoded by
   * an integer
   */
  @Pure
  public static String getString(int code) {
    if (code == mask) return "";
    if (code == maskAF_CF_OF_PF_SF_ZF) return " AF CF OF PF SF ZF";
    // Not a common case, construct it...
    String s = "";
    if ((code & maskAF) != 0) s += " AF";
    if ((code & maskCF) != 0) s += " CF";
    if ((code & maskOF) != 0) s += " OF";
    if ((code & maskPF) != 0) s += " PF";
    if ((code & maskZF) != 0) s += " ZF";
    if ((code & maskC0) != 0) s += " CO";
    if ((code & maskC1) != 0) s += " C1";
    if ((code & maskC2) != 0) s += " C2";
    if ((code & maskC3) != 0) s += " C3";
    if ((code & maskTR) != 0) s += " TR";
    if ((code & maskESP) != 0) s += " ESP";
    return s;
  }

  /**
   * @param code an integer that encodes a set of physical registers
   * @param ir the governing IR
   * @return an enumeration of the physical registers embodied by a code
   */
  public static PDUEnumeration enumerate(int code, IR ir) {
    return new PDUEnumeration(code, ir);
  }

  /**
   * @param ir the governing IR
   * @return an enumeration of all physical registers that code be
   *         implicitly defed/used
   */
  public static PDUEnumeration enumerateAllImplicitDefUses(IR ir) {
    return new PDUEnumeration(maskALL, ir);
  }

  /**
   * A class to enumerate physical registers based on a code.
   */
  public static final class PDUEnumeration implements Enumeration<Register> {
    private int code;
    private int curMask;
    private PhysicalRegisterSet phys;

    PDUEnumeration(int c, IR ir) {
      phys = ir.regpool.getPhysicalRegisterSet();
      code = c;
      curMask = maskHIGH;
    }

    @Override
    public boolean hasMoreElements() {
      return code != 0;
    }

    @Override
    public Register nextElement() {
      while (true) {
        int curBit = code & curMask;
        code -= curBit;
        curMask = curMask >> 1;
        if (curBit != 0) return getReg(curBit, phys);
      }
    }

    // artifically make static to enable scalar replacement of
    // enumeration object without requiring this method to be inlined.
    private static Register getReg(int m, PhysicalRegisterSet phys) {
      switch (m) {
        case maskAF:
          return phys.getAF();
        case maskCF:
          return phys.getCF();
        case maskOF:
          return phys.getOF();
        case maskPF:
          return phys.getPF();
        case maskSF:
          return phys.getSF();
        case maskZF:
          return phys.getZF();
        case maskC0:
          return phys.getC0();
        case maskC1:
          return phys.getC1();
        case maskC2:
          return phys.getC2();
        case maskC3:
          return phys.getC3();
        case maskTR:
          return phys.getTR();
        case maskESP:
          return phys.getESP();
      }
      OptimizingCompilerException.UNREACHABLE();
      return null; // placate jikes.
    }
  }
}
