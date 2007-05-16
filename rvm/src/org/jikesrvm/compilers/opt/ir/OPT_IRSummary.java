/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt.ir;

import static org.jikesrvm.compilers.opt.ir.OPT_Operators.BOUNDS_CHECK;

/**
 * General utilities to summarize an IR
 */
public final class OPT_IRSummary {

  /**
   * Does this IR have a bounds check expression?
   */
  public static boolean hasBoundsCheck(OPT_IR ir) {
    for (OPT_InstructionEnumeration e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      OPT_Instruction s = e.next();
      if (s.operator == BOUNDS_CHECK) {
        return true;
      }
    }
    return false;
  }
}



