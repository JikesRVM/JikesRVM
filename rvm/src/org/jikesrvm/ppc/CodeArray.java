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
package org.jikesrvm.ppc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.vmmagic.pragma.Uninterruptible;

/**
 * CodeArray represents a code object (contiguous memory region containing code).
 * The types of the access methods are platform-dependent.
 */
@Uninterruptible
public abstract class CodeArray {
  private int[] data;

  public CodeArray(int size) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be unreachable
    data = new int[size];
  }

  public int get(int index) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    return data[index];
  }

  public void set(int index, int v) {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    data[index] = v;
  }

  public int length() {
    if (VM.VerifyAssertions && VM.runningVM) VM._assert(VM.NOT_REACHED);  // should be hijacked
    return data.length;
  }

  public Object getBacking() {
    if (!VM.writingImage) VM.sysFail("CodeArray.getBacking called when not writing boot image");
    return data;
  }

  /**
   * A helper class to contain the 'real' methods of CodeArray.
   * Because Jikes RVM believes that CodeArray is really a Code[]
   * (ie, an array of primitives), we cannot define non-hijacked methods
   * on the 'class' CodeArray.
   */
  public static class Factory {
    static {
      Code x = null; // force compilation of Code wrapper class
    }
    /**
     * Allocate a code array big enough to contain numInstrs instructions.
     * @param numInstrs the number of instructions to copy from instrs
     * @param isHot is this an allocation of code for a hot method?
     * @return a CodeArray containing the instructions
     */
    public static ArchitectureSpecific.CodeArray create(int numInstrs, boolean isHot) {
      if (VM.runningVM) {
        return MemoryManager.allocateCode(numInstrs, isHot);
      } else {
        return ArchitectureSpecific.CodeArray.create(numInstrs);
      }
    }
  }
}
