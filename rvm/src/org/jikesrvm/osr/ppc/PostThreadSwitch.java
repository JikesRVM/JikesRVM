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
package org.jikesrvm.osr.ppc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ppc.BaselineConstants;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Code used for recover register value after on stack replacement.
 */

@Uninterruptible
public abstract class PostThreadSwitch implements BaselineConstants {

  /* This method must be inlined to keep the correctness
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

    Offset offset = myThread.fooFPOffset.plus(STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    Magic.objectAsAddress(myThread.getStack()).store(bridgeaddr, offset);

    myThread.fooFPOffset = Offset.zero();

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;
  }
}
