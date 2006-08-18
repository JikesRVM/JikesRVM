/*
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.JikesRVM.mm.mmtk;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Synchronization;
import com.ibm.JikesRVM.VM_Entrypoints;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * A counter that supports atomic increment and reset.
 *
 * $Id: SynchronizedCounter.java,v 1.2 2005/07/20 14:32:01 dframpton-oss Exp $
 *
 * @author Perry Cheng
 */
public final class SynchronizedCounter extends org.mmtk.vm.SynchronizedCounter implements Uninterruptible {

  private static Offset offset = Offset.max();

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

  // Returns the value before the add
  //
  public int increment() {
    if (VM.VerifyAssertions) VM._assert(!offset.isMax());
    return VM_Synchronization.fetchAndAdd(this, offset, 1);
  }

  public int peek () {
    return count;
  }

}
