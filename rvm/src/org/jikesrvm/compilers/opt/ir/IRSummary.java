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
package org.jikesrvm.compilers.opt.ir;

import static org.jikesrvm.compilers.opt.ir.Operators.BOUNDS_CHECK;

import java.util.Enumeration;

/**
 * General utilities to summarize an IR
 */
public final class IRSummary {

  /**
   * Does this IR have a bounds check expression?
   */
  public static boolean hasBoundsCheck(IR ir) {
    for (Enumeration<Instruction> e = ir.forwardInstrEnumerator(); e.hasMoreElements();) {
      Instruction s = e.nextElement();
      if (s.operator == BOUNDS_CHECK) {
        return true;
      }
    }
    return false;
  }
}



