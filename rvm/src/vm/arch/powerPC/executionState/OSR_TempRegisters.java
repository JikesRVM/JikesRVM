/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
/** 
 * Temporary resiter set.
 * see: VM_Registers
 *
 * @author Feng Qian
 */
public class OSR_TempRegisters implements VM_Constants {

  VM_Address ip;        // next instruction address
  int    gprs[];
  double fprs[];

  /* hold CR, XER, CTR */
  int    cr;
  int    xer;
  int    ctr;

  /* if a GPR hold a reference to an object, we convert the raw memory
   * address to a reference. When objs[i] is null, the GPR[i] is not
   * holding a reference.
   */
  Object objs[];

  public OSR_TempRegisters(VM_Registers contextRegisters) {
    gprs = new int[NUM_GPRS];
    fprs = new double[NUM_FPRS];
    objs = new Object[NUM_GPRS];

    System.arraycopy(contextRegisters.gprs,
		     0,
		     gprs,
		     0,
		     NUM_GPRS);
    System.arraycopy(contextRegisters.fprs,
		     0,
		     fprs,
		     0,
		     NUM_FPRS);
    ip = contextRegisters.ip;
  }

  public void dumpContents() {
    System.err.println("OSR_TempRegister: @"+ip.toInt());
    System.err.println("  GPRS: ");
    for (int i=0; i<NUM_GPRS; i++) {
      System.err.println("    ("+i+","+gprs[i]+")");
    }

    System.err.println();
    System.err.println("  OBJS: ");
    for (int i=0; i<NUM_GPRS; i++) {
      if (objs[i] != null) {
	System.err.println("    ("+i+","+objs[i]+")");
      }
    }

    System.err.println();
    System.err.println("  FPRS  ");
    for (int i=0; i<NUM_FPRS; i++) {
      System.err.println("    ("+i+","+fprs[i]+")");
    }
  }
}
