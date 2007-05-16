/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;

import java.util.LinkedList;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import org.jikesrvm.compilers.opt.ir.OsrPoint;

/**
 * Variable map element (osr instruction, LinkedList MethodVariables)
 */
public class OSR_VariableMapElement {
  public OPT_Instruction osr;
  public LinkedList<OSR_MethodVariables> mvars;

  public OSR_VariableMapElement(OPT_Instruction inst, LinkedList<OSR_MethodVariables> methVars) {
    if (VM.VerifyAssertions) {
      VM._assert(OsrPoint.conforms(inst));
    }

    this.osr = inst;
    this.mvars = methVars;
  }

  public String toString() {
    StringBuffer buf = new StringBuffer("  ");
    buf.append(this.osr.toString()).append("\n");
    for (int i = 0, n = this.mvars.size(); i < n; i++) {
      buf.append(i);
      buf.append("  ");
      buf.append(this.mvars.get(i).toString());
      buf.append("\n");
    }

    return new String(buf);
  }
}
