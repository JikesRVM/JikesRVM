/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;
/**
 * Variable map element (osr instruction, LinkedList MethodVariables) 
 *
 * @author Feng Qian
 */
public class OSR_VariableMapElement {
  public OPT_Instruction osr;
  public LinkedList mvars;

  public OSR_VariableMapElement(OPT_Instruction inst,
                                   LinkedList methVars) {
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
