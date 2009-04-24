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
package org.jikesrvm.osr.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
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
public abstract class PostThreadSwitch implements BaselineConstants {

  /**
   * This method must not be inlined to keep the correctness
   * This method is called at the end of threadSwitch, the caller
   * is threadSwitchFrom<...>
   */
  @NoInline
  public static void postProcess(RVMThread myThread) {

    /* We need to generate thread specific code and install new code.
    * We have to make sure that no GC happens from here and before
    * the new code get executed.
    */
    // add branch instruction from CTR.
    ArchitectureSpecific.CodeArray bridge = myThread.bridgeInstructions;

    Address bridgeaddr = Magic.objectAsAddress(bridge);

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("osr post processing\n");
    }

    Offset offset = myThread.tsFPOffset.plus(STACKFRAME_RETURN_ADDRESS_OFFSET);
    Magic.objectAsAddress(myThread.getStack()).store(bridgeaddr, offset);

    myThread.tsFPOffset = Offset.zero();

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;

    // no GC should happen until the glue code gets executed.
  }
}
