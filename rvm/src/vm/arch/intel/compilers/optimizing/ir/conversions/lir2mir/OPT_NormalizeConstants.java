/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;

/**
 * Normalize the use of constants in the LIR
 * to match the patterns supported in LIR2MIR.rules
 *
 * @author Dave Grove
 */
abstract class OPT_NormalizeConstants {

  /**
   * Nothing to do for IA32. 
   * 
   * @param ir IR to normalize
   */
  static void perform(OPT_IR ir) { 
    return; 
  }

  /**
   * IA32 supports 32 bit int immediates, so nothing to do.
   */
  static OPT_Operand asImmediateOrReg (OPT_Operand addr, 
				       OPT_Instruction s, 
				       OPT_IR ir) {
    return  addr;
  }

}
