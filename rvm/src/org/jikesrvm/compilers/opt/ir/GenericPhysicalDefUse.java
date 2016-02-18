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

import org.jikesrvm.VM;
import java.util.Enumeration;

public class GenericPhysicalDefUse {

  public static int getMaskTSPUses() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskTSPUses;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskTSPUses;
    }
  }

  public static int getMaskTSPDefs() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskTSPUses;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskTSPUses;
    }
  }

  public static String getString(int code) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.getString(code);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.getString(code);
    }
  }

  public static Enumeration<Register> enumerate(int code, IR ir) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.enumerate(code,ir);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.enumerate(code,ir);
    }
  }

  public static Enumeration<Register> enumerateAllImplicitDefUses(IR ir) {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.enumerateAllImplicitDefUses(ir);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.enumerateAllImplicitDefUses(ir);
    }
  }

  public static int mask() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.mask;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.mask;
    }
  }
  public static int maskcallDefs() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskcallDefs;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskcallDefs;
    }
  }

  public static int maskcallUses() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskcallUses;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskcallUses;
    }
  }

  public static int maskAF_CF_OF_PF_SF_ZF() {
    if (VM.VerifyAssertions) VM._assert(VM.BuildForIA32);
    return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskAF_CF_OF_PF_SF_ZF;
  }

  public static int maskCF() {
    if (VM.VerifyAssertions) VM._assert(VM.BuildForIA32);
    return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskCF;
  }

  public static int maskIEEEMagicUses() {
    if (VM.BuildForIA32) {
      return org.jikesrvm.compilers.opt.ir.ia32.PhysicalDefUse.maskIEEEMagicUses;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      return org.jikesrvm.compilers.opt.ir.ppc.PhysicalDefUse.maskIEEEMagicUses;
    }
  }
}
