/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.adaptive.*;
/**
 * OSR_CodeInstaller adjusts registers and return address to make a 
 * specialized thread as a normal thread get scheduled. The method
 * prologue ( machine code ) is adjusted to cooperate with the code
 * installer.
 *
 * @author Feng Qian
 */
public class OSR_CodeInstaller implements VM_Constants, VM_BaselineConstants {

  /* install the newly compiled instructions. */
  public static boolean install(OSR_ExecutionState state, 
                         VM_CompiledMethod cm) {

    VM_Thread thread = state.getThread();
    byte[] stack = thread.stack;

    int tsfromFPOffset = state.getTSFPOffset();
    int fooFPOffset    = state.getFPOffset();

    // we are going to dynamically generate some code recover 
    // register values from the stack frame.
    int foomid = VM_Magic.getIntAtOffset(stack, 
                           fooFPOffset + STACKFRAME_METHOD_ID_OFFSET);

    VM_CompiledMethod foo = VM_CompiledMethods.getCompiledMethod(foomid);
    int cType = foo.getCompilerType();

    VM_Assembler asm = new VM_Assembler(0, VM.TraceOnStackReplacement);
    if (cType == VM_CompiledMethod.BASELINE) {
      // do nothing 
    } else if (cType == VM_CompiledMethod.OPT) {
      /////////////////////////////////////
      ////// recover saved registers.
      /////////////////////////////////////         
      VM_OptCompiledMethod  fooOpt = (VM_OptCompiledMethod)foo;
        
      // foo definitely not save volatile.
      boolean saveVolatile = fooOpt.isSaveVolatile();
      if (VM.VerifyAssertions) {
        VM._assert(!saveVolatile);
      }
        
      int offset = fooOpt.getUnsignedNonVolatileOffset();

      // recover nonvolatile GPRs
      int firstGPR = fooOpt.getFirstNonVolatileGPR();
      if (firstGPR != -1) {
        for (int i=firstGPR;
             i<=LAST_NONVOLATILE_GPR;
             i++) {
          asm.emitLAddr(i, offset, FP);
          offset += BYTES_IN_STACKSLOT;
        }
      }

      // it may have a padding before FPRs.
      offset = VM_Memory.alignUp(offset,  BYTES_IN_STACKSLOT) ;
        
      // recover nonvolatile FPRs
      int firstFPR = fooOpt.getFirstNonVolatileFPR();
      if (firstFPR != -1) {
        for (int i=firstFPR;
             i <= LAST_NONVOLATILE_FPR;
             i++) {
          asm.emitLFD(i, offset, FP);
          offset += 2*BYTES_IN_STACKSLOT;
        }
      }
    }

    if (VM.VerifyAssertions) {
      Object jtocContent = VM_Statics.getSlotContentsAsObject(cm.getOsrJTOCoffset() >> 2);
      VM._assert(jtocContent == cm.getInstructions());
    }   
    
    // load address of newInstructions from JTOC
    asm.emitLAddrToc(S0, cm.getOsrJTOCoffset());
    // mov CTR addr
    asm.emitMTCTR(S0);
    // lwz FP, 0(FP)
    asm.emitLAddr(FP, 0, FP);
    // lwz T0, NEXT_INSTR(FP)
    asm.emitLAddr(S0, STACKFRAME_NEXT_INSTRUCTION_OFFSET, FP);
    // mov LR, addr
    asm.emitMTLR(S0);
    // bctr
    asm.emitBCCTR();

    VM_MachineCode mc = asm.makeMachineCode();  

    // mark the thread as waiting for on stack replacement.
    thread.isWaitingForOsr = true;
    thread.bridgeInstructions = mc.getInstructions();
    thread.fooFPOffset = fooFPOffset;

    VM_Address bridgeaddr = 
      VM_Magic.objectAsAddress(thread.bridgeInstructions);

    VM_Memory.sync(bridgeaddr, 
                   thread.bridgeInstructions.length() << LG_INSTRUCTION_WIDTH);

    if (VM.LogAOSEvents) {
      VM_AOSLogging.logOsrEvent("OSR code installation succeeded");
    }

    return true;
  }
}
