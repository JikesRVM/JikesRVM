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
package org.jikesrvm.osr.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.ia32.VM_BaselineConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * A class helps schedule OSRed method, it is called right after thread switch
 * and highly depends on the calling convention. It should not be interrupted
 * because it deals with row instruction address.
 */
@Uninterruptible
public abstract class OSR_PostThreadSwitch implements VM_BaselineConstants {

  /**
   * This method must not be inlined to keep the correctness
   * This method is called at the end of threadSwitch, the caller
   * is threadSwitchFrom<...>
   */
  @NoInline
  public static void postProcess(VM_Thread myThread) {

    /* We need to generate thread specific code and install new code.
    * We have to make sure that no GC happens from here and before
    * the new code get executed.
    */
    // add branch instruction from CTR.
    ArchitectureSpecific.VM_CodeArray bridge = myThread.bridgeInstructions;

    Address bridgeaddr = VM_Magic.objectAsAddress(bridge);

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("osr post processing\n");
    }

    Offset offset = myThread.tsFPOffset.plus(STACKFRAME_RETURN_ADDRESS_OFFSET);
    VM_Magic.objectAsAddress(myThread.stack).store(bridgeaddr, offset);

    myThread.tsFPOffset = Offset.zero();

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;

    // no GC should happen until the glue code gets executed.
  }
}
