/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
import com.ibm.JikesRVM.*;

import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * A class helps schedule OSRed method, it is called right after thread switch
 * and highly depends on the calling convention. It should not be interrupted
 * because it deals with row instruction address.
 *
 * @author Feng Qian
 */
public class OSR_PostThreadSwitch implements VM_BaselineConstants, Uninterruptible {

  /**
   * This method must not be inlined to keep the correctness 
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

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("osr post processing\n");
    }
        
    Offset offset = Offset.fromInt(myThread.tsFPOffset + STACKFRAME_RETURN_ADDRESS_OFFSET);
    VM_Magic.objectAsAddress(myThread.stack).store(bridgeaddr, offset);

    myThread.tsFPOffset = 0;

    myThread.isWaitingForOsr = false;
    myThread.bridgeInstructions = null;

    // no GC should happen until the glue code gets executed.
  }
}
