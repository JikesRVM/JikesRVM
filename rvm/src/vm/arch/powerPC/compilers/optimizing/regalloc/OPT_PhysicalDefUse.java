/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.util.Enumeration;

/**
 * This class provides utilities to record defs and uses of physical
 * registers by IR operators.
 *
 * @author Stephen Fink
 */
class OPT_PhysicalDefUse {

  // constants used to encode defs/uses of physical registers
  final static int mask         = 0x00;  // empty mask
  final static int maskC0       = 0x01;
  final static int maskXER      = 0x02;
  final static int maskLR       = 0x04;
  final static int maskJTOC     = 0x08;
  final static int maskCTR      = 0x10;
  final static int maskPR       = 0x20;

  // Meta mask for the enumeration.
  private final static int maskHIGH = 0x20;
  private final static int maskALL  = 0x3F;

  final static int maskC0_XER   = maskC0 | maskXER;
  final static int maskJTOC_LR  = maskJTOC | maskLR;
  final static int maskJTOC_CTR = maskJTOC | maskCTR;
  final static int maskcallDefs = maskLR;
  final static int maskcallUses = maskJTOC;
  final static int maskIEEEMagicUses = maskJTOC;
  final static int maskTSPDefs  = maskPR;
  final static int maskTSPUses  = maskJTOC;

  /**
   * @return a string representation of the physical registers encoded by
   * an integer
   */
  static String getString(int code) {
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
  static PDUEnumeration enumerate(int code, OPT_IR ir) {
    return new PDUEnumeration(code,ir);
  }

  /**
   * @param ir the governing IR
   * @return an enumeration of all physical registers that code be 
   *         implicitly defed/used
   */
  static PDUEnumeration enumerateAllImplicitDefUses(OPT_IR ir) {
    return new PDUEnumeration(maskALL,ir);
  }

  /**
   * A class to enumerate physical registers based on a code.
   */
  static final class PDUEnumeration implements Enumeration {
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

    public Object nextElement() {
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
      OPT_OptimizingCompilerException.UNREACHABLE();
      return null; // placate jikes.
    }
  }
}
