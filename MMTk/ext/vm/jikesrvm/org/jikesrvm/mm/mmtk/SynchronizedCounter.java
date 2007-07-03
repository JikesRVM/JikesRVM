/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.jikesrvm.runtime.VM_Entrypoints;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * A counter that supports atomic increment and reset.
 */
@Uninterruptible public final class SynchronizedCounter extends org.mmtk.vm.SynchronizedCounter {

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
