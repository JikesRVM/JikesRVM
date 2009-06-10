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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;
import org.jikesrvm.scheduler.Synchronization;
import org.jikesrvm.runtime.Entrypoints;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * A counter that supports atomic increment and reset.
 */
@Uninterruptible
public final class SynchronizedCounter extends org.mmtk.vm.SynchronizedCounter {

  private static Offset offset = Offset.max();

  public static void boot() {
    offset = Entrypoints.synchronizedCounterField.getOffset();
  }

  @Entrypoint
  private int count = 0;

  public int reset() {
    //    int offset = Interface.synchronizedCounterOffset;
    int oldValue = count;
    int actualOldValue = Synchronization.fetchAndAdd(this, offset, -oldValue);
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
    return Synchronization.fetchAndAdd(this, offset, 1);
  }

  public int peek() {
    return count;
  }

}
