/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr.ppc;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ppc.VM_BaselineConstants;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * Code used for recover register value after on stack replacement.
 *
 * @author Feng Qian
 */

@Uninterruptible public abstract class OSR_PostThreadSwitch implements VM_BaselineConstants {

  /* This method must be inlined to keep the correctness 
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
    ArchitectureSpecific.VM_CodeArray bridge   = myThread.bridgeInstructions;
      
    Address bridgeaddr = VM_Magic.objectAsAddress(bridge);

    Offset offset = myThread.fooFPOffset.plus(STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    VM_Magic.objectAsAddress(myThread.stack).store(bridgeaddr, offset);
        
    myThread.fooFPOffset = Offset.zero();

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;
  }
}
