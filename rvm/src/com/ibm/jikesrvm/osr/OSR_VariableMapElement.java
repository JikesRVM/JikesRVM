/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package com.ibm.jikesrvm.osr;

import com.ibm.jikesrvm.*;
import com.ibm.jikesrvm.opt.ir.*;
import java.util.*;
/**
 * Variable map element (osr instruction, LinkedList MethodVariables) 
 *
 * @author Feng Qian
 */
public class OSR_VariableMapElement {
  public OPT_Instruction osr;
  public LinkedList<OSR_MethodVariables> mvars;

  public OSR_VariableMapElement(OPT_Instruction inst,
                                   LinkedList<OSR_MethodVariables> methVars) {
    if (VM.VerifyAssertions) {
      VM._assert(OsrPoint.conforms(inst));
    }

    this.osr   = inst;
    this.mvars = methVars;
  }
 
  public String toString() {
    StringBuffer buf = new StringBuffer("  ");
    buf.append(this.osr.toString()+"\n");
    for (int i=0, n=this.mvars.size(); i<n; i++) {
      buf.append(i);
      buf.append("  ");
      buf.append(this.mvars.get(i).toString());
      buf.append("\n");
    }

    return new String(buf);
  }
}
