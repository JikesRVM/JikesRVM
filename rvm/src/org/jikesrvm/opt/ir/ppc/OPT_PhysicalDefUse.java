/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.opt.ir.ppc;

import java.util.Enumeration;
import org.jikesrvm.opt.ir.OPT_IR;
import org.jikesrvm.opt.ir.OPT_Register;

/**
 * This class provides utilities to record defs and uses of physical
 * registers by IR operators.
 *
 * @author Stephen Fink
 */
public abstract class OPT_PhysicalDefUse {

  // constants used to encode defs/uses of physical registers
  public static final int mask         = 0x00;  // empty mask
  public static final int maskC0       = 0x01;
  public static final int maskXER      = 0x02;
  public static final int maskLR       = 0x04;
  public static final int maskJTOC     = 0x08;
  public static final int maskCTR      = 0x10;
  public static final int maskPR       = 0x20;

  // Meta mask for the enumeration.
  private static final int maskHIGH = 0x20;
  private static final int maskALL  = 0x3F;

  public static final int maskC0_XER   = maskC0 | maskXER;
  public static final int maskJTOC_LR  = maskJTOC | maskLR;
  public static final int maskJTOC_CTR = maskJTOC | maskCTR;
  public static final int maskcallDefs = maskLR;
  public static final int maskcallUses = maskJTOC;
  public static final int maskIEEEMagicUses = maskJTOC;
  public static final int maskTSPDefs  = maskPR;
  public static final int maskTSPUses  = maskJTOC;

  /**
   * @return a string representation of the physical registers encoded by
   * an integer
   */
  public static String getString(int code) {
    if (code == mask) return "";
    // Not a common case, construct it...
    String s = "";
    if ((code & maskC0) != 0) s += " C0";
    if ((code & maskXER) != 0) s += " XER";
    if ((code & maskLR) != 0) s += " LR";
    if ((code & maskJTOC) != 0) s += " JTOC";
    if ((code & maskCTR) != 0) s += " CTR";
    if ((code & maskPR) != 0) s += " PR";
    return s;
  }

  /**
   * @param code an integer that encodes a set of physical registers
   * @param ir the governing IR
   * @return an enumeration of the physical registers embodied by a code
   */
  public static PDUEnumeration enumerate(int code, OPT_IR ir) {
    return new PDUEnumeration(code,ir);
  }

  /**
   * @param ir the governing IR
   * @return an enumeration of all physical registers that code be 
   *         implicitly defed/used
   */
  public static PDUEnumeration enumerateAllImplicitDefUses(OPT_IR ir) {
    return new PDUEnumeration(maskALL,ir);
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
      switch(m) {
      case maskC0: return phys.getConditionRegister(0);
      case maskXER: return phys.getXER();
      case maskLR: return phys.getLR();
      case maskJTOC: return phys.getJTOC();
      case maskCTR: return phys.getCTR();
      case maskPR: return phys.getPR();
      }
      org.jikesrvm.opt.OPT_OptimizingCompilerException.UNREACHABLE();
      return null; // placate jikes.
    }
  }
}
