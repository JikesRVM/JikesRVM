/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Entrypoints;

/**
 * A counter that supports atomic increment and reset.
 *
 * @author Perry Cheng
 */
public final class SynchronizedCounter implements VM_Uninterruptible {

  private static int offset = -1;

  public static void boot() {
    offset = VM_Entrypoints.synchronizedCounterField.getOffset();
  }

  private int count = 0;

  public int reset() {
    //    int offset = VM_Interface.synchronizedCounterOffset;
    int oldValue = count;
    int actualOldValue = VM_Synchronization.fetchAndAdd(this, offset, -oldValue);
    if (actualOldValue != oldValue) {
      VM.sysWriteln("oldValue = ", oldValue);
      VM.sysWriteln("actualOldValue = ", actualOldValue);
      VM.sysFail("Concurrent use of SynchronizedCounter.reset");
    }
    return oldValue;
  }

  public int increment() {
    // int offset = VM_Interface.synchronizedCounterOffset;
    return VM_Synchronization.fetchAndAdd(this, offset, 1);
  }

}
