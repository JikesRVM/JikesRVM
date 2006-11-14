/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.mm.mmtk;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.VM_Synchronization;
import com.ibm.jikesrvm.VM_Entrypoints;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Offset;

/**
 * A counter that supports atomic increment and reset.
 *
 * $Id$
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
