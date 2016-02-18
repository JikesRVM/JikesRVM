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
package org.jikesrvm.osr.ia32;

import static org.jikesrvm.ia32.RegisterConstants.NUM_FPRS;
import static org.jikesrvm.ia32.RegisterConstants.NUM_GPRS;

import org.jikesrvm.architecture.AbstractRegisters;
import org.jikesrvm.util.Services;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.WordArray;

/**
 * Temporary register set.
 * see: Registers
 */
public class TempRegisters {

  final Address ip;        // next instruction address
  final WordArray gprs;
  final double[] fprs;

  /**
   * if a GPR hold a reference to an object, we convert the raw memory
   * address to a reference. When objs[i] is null, the GPR[i] is not
   * holding a reference.
   */
  final Object[] objs;

  public TempRegisters(AbstractRegisters contextRegisters) {
    gprs = WordArray.create(NUM_GPRS);
    fprs = new double[NUM_FPRS];
    objs = new Object[NUM_GPRS];

    for (int i = 0; i < NUM_GPRS; i++) {
      gprs.set(i, contextRegisters.getGPRs().get(i));
    }
    System.arraycopy(contextRegisters.getFPRs(), 0, fprs, 0, NUM_FPRS);
    ip = contextRegisters.getIP();
  }

  public void dumpContents() {
    System.err.println("OSR_TempRegister: @" + Services.addressAsHexString(ip));
    System.err.println("  GPRS: ");
    for (int i = 0; i < NUM_GPRS; i++) {
      System.err.println("    (" + i + "," + Services.addressAsHexString(gprs.get(i).toAddress()) + ")");
    }

    System.err.println();
    System.err.println("  OBJS: ");
    for (int i = 0; i < NUM_GPRS; i++) {
      if (objs[i] != null) {
        System.err.println("    (" + i + "," + objs[i] + ")");
      }
    }

    System.err.println();
    System.err.println("  FPRS  ");
    for (int i = 0; i < NUM_FPRS; i++) {
      System.err.println("    (" + i + "," + fprs[i] + ")");
    }
  }
}
