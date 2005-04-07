/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM;

import org.vmmagic.pragma.*;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;

/**
 * VM_CodeArray represents a code object (contiguous memory region containing code).
 * The types of the access methods are platform-dependent.
 *
 * @author Perry Cheng
 */
public final class VM_CodeArray implements Uninterruptible {
  private int [] data;

  // only intended to be called from VM_CodeArray.factory
  static VM_CodeArray create (int size) throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new VM_CodeArray(size);
  }

  private VM_CodeArray (int size) throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be unreachable
    data = new int[size];
  }

  public int get (int index) {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set (int index, int v) {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    data[index] = v;
  }

  public int length() {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return data.length;
  }

  public Object getBacking() {
    if (!VM.writingImage)
      VM.sysFail("VM_CodeArray.getBacking called when not writing boot image");
    return data;
  }

  /**
   * A helper class to contain the 'real' methods of VM_CodeArray.
   * Because Jikes RVM believes that VM_CodeArray is really a Code[]
   * (ie, an array of primitives), we cannot define non-hijacked methods
   * on the 'class' VM_CodeArray.
   * 
   * @author Dave Grove
   */
  public static class Factory {
    /**
     * Allocate a code array big enough to contain numInstrs instructions.
     * @param numInstrs the number of instructions to copy from instrs
     * @param isHot is this an allocation of code for a hot method?
     * @return a VM_CodeArray containing the instructions
     */
    public static VM_CodeArray create(int numInstrs, boolean isHot) {
      if (VM.runningVM) {
        return MM_Interface.allocateCode(numInstrs, isHot);
      } else {
        return VM_CodeArray.create(numInstrs);
      }
    }
  }
}
