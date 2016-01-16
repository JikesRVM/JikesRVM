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
package org.jikesrvm.architecture;

import org.jikesrvm.VM;

import org.vmmagic.pragma.Uninterruptible;

public class ArchConstants {

  @Uninterruptible
  public static int getLogInstructionWidth() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.LG_INSTRUCTION_WIDTH;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.ppc.RegisterConstants.LG_INSTRUCTION_WIDTH;
    }
  }

  @Uninterruptible
  public static int getNumberOfGPRs() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.NUM_GPRS;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.ppc.RegisterConstants.NUM_GPRS;
    }
  }

  @Uninterruptible
  public static int getNumberOfFPRs() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.NUM_FPRS;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.ppc.RegisterConstants.NUM_FPRS;
    }
  }

  @Uninterruptible
  public static int getInstructionWidth() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.ia32.RegisterConstants.INSTRUCTION_WIDTH;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.ppc.RegisterConstants.INSTRUCTION_WIDTH;
    }
  }

}
