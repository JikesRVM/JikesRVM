/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_Synchronization;

/**
 * A counter that supports atomic increment and reset.
 *
 * @author Perry Cheng
 */
public final class SynchronizedCounter implements VM_Uninterruptible {

  private int count = 0;

  public int reset() {
    int offset = MM_Interface.synchronizedCounterOffset;
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
    int offset = MM_Interface.synchronizedCounterOffset;
    return VM_Synchronization.fetchAndAdd(this, offset, 1);
  }

}
