/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.baseline.VM_BaselineCompiledMethod;
import org.jikesrvm.compilers.baseline.ppc.VM_Compiler;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_CompiledMethods;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Memory;
import org.jikesrvm.runtime.VM_Statics;
import org.jikesrvm.scheduler.VM_Thread;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.compilers.common.assembler.ppc.VM_Assembler;
import org.jikesrvm.ppc.VM_BaselineConstants;
import org.jikesrvm.ppc.VM_MachineCode;
import org.jikesrvm.compilers.opt.VM_OptCompiledMethod;
import org.jikesrvm.osr.OSR_ExecutionState;
import org.jikesrvm.adaptive.util.VM_AOSLogging;

import org.vmmagic.unboxed.*;

/**
 * OSR_CodeInstaller adjusts registers and return address to make a 
 * specialized thread as a normal thread get scheduled. The method
 * prologue ( machine code ) is adjusted to cooperate with the code
 * installer.
 *
 * @author Feng Qian
 */
public abstract class OSR_CodeInstaller implements VM_BaselineConstants {

  /* install the newly compiled instructions. */
  public static boolean install(OSR_ExecutionState state, 
                         VM_CompiledMethod cm) {

    VM_Thread thread = state.getThread();
    byte[] stack = thread.stack;

    Offset fooFPOffset    = state.getFPOffset();

    // we are going to dynamically generate some code recover 
    // register values from the stack frame.
    int foomid = VM_Magic.getIntAtOffset(stack, fooFPOffset.plus(STACKFRAME_METHOD_ID_OFFSET));

    VM_CompiledMethod foo = VM_CompiledMethods.getCompiledMethod(foomid);
    int cType = foo.getCompilerType();

    VM_Assembler asm = new ArchitectureSpecific.VM_Assembler(0, VM.TraceOnStackReplacement);
    
    /////////////////////////////////////
    ////// recover saved registers.
    /////////////////////////////////////         
    if (cType == VM_CompiledMethod.BASELINE) {
      VM_BaselineCompiledMethod  bcm = (VM_BaselineCompiledMethod)foo;
      int offset = VM_Compiler.getFrameSize(bcm);
      for (int i = bcm.getLastFloatStackRegister(); i >= FIRST_FLOAT_LOCAL_REGISTER; --i) {    
        offset -= BYTES_IN_DOUBLE;
        asm.emitLFD(i, offset, FP);
      }
      for (int i = bcm.getLastFixedStackRegister(); i >= FIRST_FIXED_LOCAL_REGISTER; --i) {    
        offset -= BYTES_IN_ADDRESS;
        asm.emitLAddr(i, offset, FP);
      }
    } else if (cType == VM_CompiledMethod.OPT) {
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

      // recover nonvolatile FPRs
      int firstFPR = fooOpt.getFirstNonVolatileFPR();
      if (firstFPR != -1) {
        for (int i=firstFPR;
             i <= LAST_NONVOLATILE_FPR;
             i++) {
          asm.emitLFD(i, offset, FP);
          offset += BYTES_IN_DOUBLE;
        }
      }
    }

    if (VM.VerifyAssertions) {
      Object jtocContent = VM_Statics.getSlotContentsAsObject(cm.getOsrJTOCoffset());
      VM._assert(jtocContent == cm.getEntryCodeArray());
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

    Address bridgeaddr = 
      VM_Magic.objectAsAddress(thread.bridgeInstructions);

    VM_Memory.sync(bridgeaddr, 
                   thread.bridgeInstructions.length() << LG_INSTRUCTION_WIDTH);

    VM_AOSLogging.logOsrEvent("OSR code installation succeeded");

    return true;
  }
}
