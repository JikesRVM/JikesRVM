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
package org.jikesrvm.compilers.common;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.vmmagic.pragma.Uninterruptible;

/**
 * CodeArray represents a code object (contiguous memory region containing code).
 * The types of the access methods are platform-dependent.
 */
@Uninterruptible
public final class CodeArray {
  /** backing array for PPC code arrays during boot image creation */
  private final int[] ppc_data;
  /** backing array for ARM code arrays during boot image creation */
  private final int[] arm_data;
  /** backing array for x86 code arrays during boot image creation */
  private final byte[] x86_data;

  CodeArray(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be unreachable
    if (VM.BuildForIA32) {
      x86_data = new byte[size];
      ppc_data = null;
      arm_data = null;
    } else if (VM.BuildForPowerPC) {
      ppc_data = new int[size];
      x86_data = null;
      arm_data = null;
    } else if (VM.BuildForARM) {
      arm_data = new int[size];
      x86_data = null;
      ppc_data = null;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  public int get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    if (VM.BuildForIA32) {
      return x86_data[index];
    } else if (VM.BuildForPowerPC) {
      return ppc_data[index];
    } else if (VM.BuildForARM) {
      return arm_data[index];
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return 0;
    }
  }

  public void set(int index, int v) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    if (VM.BuildForIA32) {
      byte bv = (byte)v;
      if (VM.VerifyAssertions) VM._assert(v == bv);
      x86_data[index] = bv;
    } else if (VM.BuildForPowerPC) {
      ppc_data[index] = v;
    } else if (VM.BuildForARM) {
      arm_data[index] = v;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  public int length() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    if (VM.BuildForIA32) {
      return x86_data.length;
    } else if (VM.BuildForPowerPC) {
      return ppc_data.length;
    } else if (VM.BuildForARM) {
      return arm_data.length;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return 0;
    }
  }

  public Object getBacking() {
    if (!VM.writingImage) VM.sysFail("CodeArray.getBacking called when not writing boot image");
    if (VM.BuildForIA32) {
      return x86_data;
    } else if (VM.BuildForPowerPC) {
      return ppc_data;
    } else if (VM.BuildForARM) {
      return arm_data;
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return null;
    }
  }

  /**
   * A helper class to contain the 'real' methods of CodeArray.
   * Because Jikes RVM believes that CodeArray is really a Code[]
   * (ie, an array of primitives), we cannot define non-hijacked methods
   * on the 'class' CodeArray.
   */
  public static class Factory {
    /**
     * Allocate a code array big enough to contain numInstrs instructions.
     * @param numInstrs the number of instructions to copy from instrs
     * @param isHot is this an allocation of code for a hot method?
     * @return a CodeArray containing the instructions
     */
    public static CodeArray create(int numInstrs, boolean isHot) {
      if (VM.runningVM) {
        return MemoryManager.allocateCode(numInstrs, isHot);
      } else {
        return BootImageCreate.create(numInstrs, isHot);
      }
    }
  }

  /**
   * Class to create CodeArrays in the boot image that isn't compiled into the VM
   */
  private static class BootImageCreate {
    static CodeArray create(int numInstrs, boolean isHot) {
      return new CodeArray(numInstrs);
    }
  }
}
