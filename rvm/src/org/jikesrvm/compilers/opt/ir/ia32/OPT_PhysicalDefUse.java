/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir.ia32;

import java.util.Enumeration;
import org.jikesrvm.compilers.opt.OPT_OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Operator;
import org.jikesrvm.compilers.opt.ir.OPT_Register;

/**
 * This class provides utilities to record defs and uses of physical
 * registers by IR operators.
 */
public abstract class OPT_PhysicalDefUse {

  // constants used to encode defs/uses of physical registers
  public static final int mask = 0x0000;  // empty mask
  public static final int maskAF = 0x0001;
  public static final int maskCF = 0x0002;
  public static final int maskOF = 0x0004;
  public static final int maskPF = 0x0008;
  public static final int maskSF = 0x0010;
  public static final int maskZF = 0x0020;
  public static final int maskC0 = 0x0040;
  public static final int maskC1 = 0x0080;
  public static final int maskC2 = 0x0100;
  public static final int maskC3 = 0x0200;
  public static final int maskPR = 0x0400;
  // Meta mask for the enumeration.
  private static final int maskHIGH = 0x0400;
  private static final int maskALL = 0x07FF;

  public static final int maskCF_OF = maskCF | maskOF;
  public static final int maskCF_PF_ZF = maskCF | maskPF | maskZF;
  public static final int maskCF_OF_PF_SF_ZF = maskCF | maskOF | maskPF | maskSF | maskZF;
  public static final int maskAF_OF_PF_SF_ZF = maskAF | maskOF | maskPF | maskSF | maskZF;
  public static final int maskAF_CF_OF_PF_SF_ZF = maskAF | maskCF | maskOF | maskPF | maskSF | maskZF;
  public static final int maskC0_C1_C2_C3 = maskC0 | maskC1 | maskC2 | maskC3;
  public static final int maskcallDefs = maskAF_CF_OF_PF_SF_ZF;
  public static final int maskcallUses = mask;
  public static final int maskIEEEMagicUses = mask;
  public static final int maskTSPUses = mask;
  public static final int maskTSPDefs = maskAF_CF_OF_PF_SF_ZF | maskPR;

  /**
   * @return whether or not an OPT_Operator uses the EFLAGS
   */
  public static boolean usesEFLAGS(OPT_Operator op) {
    return (op.implicitUses & maskAF_CF_OF_PF_SF_ZF) != 0;
  }

  /**
   * @return whether or not an OPT_Operator uses the EFLAGS
   */
  public static boolean definesEFLAGS(OPT_Operator op) {
    return (op.implicitDefs & maskAF_CF_OF_PF_SF_ZF) != 0;
  }

  /**
   * @return a string representation of the physical registers encoded by
   * an integer
   */
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
    if ((code & maskPR) != 0) s += " PR";
    return s;
  }

  /**
   * @param code an integer that encodes a set of physical registers
   * @param ir the governing IR
   * @return an enumeration of the physical registers embodied by a code
   */
  public static PDUEnumeration enumerate(int code, OPT_IR ir) {
    return new PDUEnumeration(code, ir);
  }

  /**
   * @param ir the governing IR
   * @return an enumeration of all physical registers that code be
   *         implicitly defed/used
   */
  public static PDUEnumeration enumerateAllImplicitDefUses(OPT_IR ir) {
    return new PDUEnumeration(maskALL, ir);
  }

  /**
   * A class to enumerate physical registers based on a code.
   */
  public static final class PDUEnumeration implements Enumeration<OPT_Register> {
    private int code;
    private int curMask;
    private OPT_PhysicalRegisterSet phys;

    PDUEnumeration(int c, OPT_IR ir) {
      phys = ir.regpool.getPhysicalRegisterSet();
      code = c;
      curMask = maskHIGH;
    }

    public boolean hasMoreElements() {
      return code != 0;
    }

    public OPT_Register nextElement() {
      while (true) {
        int curBit = code & curMask;
        code -= curBit;
        curMask = curMask >> 1;
        if (curBit != 0) return getReg(curBit, phys);
      }
    }

    // artifically make static to enable scalar replacement of
    // enumeration object without requiring this method to be inlined.
    private static OPT_Register getReg(int m, OPT_PhysicalRegisterSet phys) {
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
        case maskPR:
          return phys.getPR();
      }
      OPT_OptimizingCompilerException.UNREACHABLE();
      return null; // placate jikes.
    }
  }
}
