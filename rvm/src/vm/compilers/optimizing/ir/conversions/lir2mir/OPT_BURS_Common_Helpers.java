/*
 * (C) Copyright IBM Corp. 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * Contains BURS helper functions common to all platforms.
 * 
 * @author Dave Grove
 * @author Stephen Fink
 */
abstract class OPT_BURS_Common_Helpers extends OPT_PhysicalRegisterTools
  implements OPT_Operators, OPT_PhysicalRegisterConstants {

  /** Infinte cost for a rule */
  protected static final int INFINITE = 32767;

  /**
   * The burs object
   */
  protected final OPT_BURS burs;

  /**
   * The register pool of the IR being processed
   */
  protected final OPT_RegisterPool regpool;

  OPT_BURS_Common_Helpers(OPT_BURS b) {
    burs = b;
    regpool = b.ir.regpool;
  }

  public final OPT_IR getIR() { return burs.ir; }

  protected final void EMIT(OPT_Instruction s) {
    burs.append(s);
  }

  // returns the given operand as a register
  protected final OPT_RegisterOperand R(OPT_Operand op) {
    return (OPT_RegisterOperand) op;
  }

  // returns the given operand as an integer constant
  protected final OPT_IntConstantOperand I(OPT_Operand op) {
    return (OPT_IntConstantOperand) op;
  }
   
  // returns the given operand as a long constant
  protected final OPT_LongConstantOperand L(OPT_Operand op) {
    return (OPT_LongConstantOperand) op;
  }

  // returns the integer value of the given operand
  protected final int IV(OPT_Operand op) {
    return I(op).value;
  }

  // is a == 0?
  protected final boolean ZERO(OPT_Operand a) {
    return (IV(a) == 0);
  }

  // is a == 1?
  protected final boolean ONE(OPT_Operand a) {
    return (IV(a) == 1);
  }

  // is a == -1?
  protected final boolean MINUSONE(OPT_Operand a) {
    return (IV(a) == -1);
  }

  protected final int FITS(OPT_Operand op, int numBits, int trueCost) {
    return FITS(op, numBits, trueCost, OPT_BURS_STATE.INFINITE);
  }
  protected final int FITS(OPT_Operand op, int numBits, int trueCost, int falseCost) {
    if (op.isIntConstant() && OPT_Bits.fits(IV(op),numBits)) {
      return trueCost;
    } else {
      return falseCost;
    }
  }

  // helper functions for condition operands
  protected final boolean EQ_NE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
	    (cond == OPT_ConditionOperand.NOT_EQUAL));
  }

  protected final boolean EQ_LT_LE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
	    (cond == OPT_ConditionOperand.LESS) ||
	    (cond == OPT_ConditionOperand.LESS_EQUAL));
  }

  protected final boolean EQ_GT_GE(OPT_ConditionOperand c) {
    int cond = c.value;
    return ((cond == OPT_ConditionOperand.EQUAL) ||
	    (cond == OPT_ConditionOperand.GREATER) ||
	    (cond == OPT_ConditionOperand.GREATER_EQUAL));
  }

  // condition code state
  private OPT_ConditionOperand cc;
  protected final void pushCOND(OPT_ConditionOperand c) {
    if (VM.VerifyAssertions) VM._assert(cc == null);
    cc = c ;
  }
  protected final OPT_ConditionOperand consumeCOND() {
    OPT_ConditionOperand ans = cc;
    if (VM.VerifyAssertions) {
      VM._assert(cc != null);
      cc = null;
    }
    return ans;
  }
}
