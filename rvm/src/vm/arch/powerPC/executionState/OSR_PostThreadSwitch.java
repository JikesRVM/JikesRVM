/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
import com.ibm.JikesRVM.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Code used for recover register value after on stack replacement.
 *
 * @author Feng Qian
 */

public class OSR_PostThreadSwitch implements VM_BaselineConstants, Uninterruptible {

  /* This method must be inlined to keep the correctness 
   * This method is called at the end of threadSwitch, the caller
   * is threadSwitchFrom<...>
   */
  public static void postProcess(VM_Thread myThread) 
    throws NoInlinePragma {

    /* We need to generate thread specific code and install new code.
     * We have to make sure that no GC happens from here and before 
     * the new code get executed.
     */
    // add branch instruction from CTR.
    VM_CodeArray bridge   = myThread.bridgeInstructions;
      
    Address bridgeaddr = VM_Magic.objectAsAddress(bridge);

    Offset offset = myThread.fooFPOffset.add(STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    VM_Magic.objectAsAddress(myThread.stack).store(bridgeaddr, offset);
        
    myThread.fooFPOffset = Offset.zero();

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;
  }
}
