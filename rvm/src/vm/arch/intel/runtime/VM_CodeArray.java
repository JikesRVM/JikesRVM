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
  private byte [] data;

  public static VM_CodeArray create (int size) throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return new VM_CodeArray(size);
  }

  private VM_CodeArray (int size) throws InterruptiblePragma {
    if (VM.runningVM) VM._assert(false);  // should be unreachable
    data = new byte[size];
  }

  public byte get (int index) {
    if (VM.runningVM) VM._assert(false);  // should be hijacked
    return data[index];
  }

  public void set (int index, byte v) {
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
     * Allocate a code array to contain numInstrs instructions and initialize
     * it by copying in instrs[0] to instrs[numInstrs-1].
     * @param instrs an array of instructions
     * @param numInstrs the number of instructions to copy from instrs
     * @param isHot is this an allocation of code for a hot method?
     * @return a VM_CodeArray containing the instructions
     */
    public static VM_CodeArray createAndFill(byte[] instrs,
                                             int numInstrs,
                                             boolean isHot) {
      if (VM.VerifyAssertions) VM._assert(numInstrs <= instrs.length);
      VM_CodeArray code;
      if (VM.runningVM) {
        code = MM_Interface.newCode(numInstrs, isHot);
        VM_Memory.arraycopy8Bit(instrs, 0, code, 0, numInstrs);
        return code;
      } else {
        code = VM_CodeArray.create(numInstrs);
        for (int i=0; i<numInstrs; i++) {
          code.set(i, instrs[i]);
        }
      }
      return code;
    }
  }
}
