/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.ia32.osr;
import org.jikesrvm.VM;
import org.jikesrvm.VM_Magic;
import org.jikesrvm.VM_Thread;
import org.jikesrvm.ArchitectureSpecific.VM_BaselineConstants;
import org.jikesrvm.ArchitectureSpecific.VM_CodeArray;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * A class helps schedule OSRed method, it is called right after thread switch
 * and highly depends on the calling convention. It should not be interrupted
 * because it deals with row instruction address.
 *
 * @author Feng Qian
 */
@Uninterruptible public abstract class OSR_PostThreadSwitch implements VM_BaselineConstants {

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
    VM_CodeArray bridge   = myThread.bridgeInstructions;
      
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
