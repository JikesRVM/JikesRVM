/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * General utilities to summarize an IR
 *
 * @author Stephen Fink
 */
final class OPT_IRSummary implements OPT_Operators {

  /** 
   * Does this IR have a bounds check expression?
   */
  public static boolean hasBoundsCheck (OPT_IR ir) {
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); 
        e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (s.operator == BOUNDS_CHECK)
        return  true;
    }
    return  false;
  }
}



