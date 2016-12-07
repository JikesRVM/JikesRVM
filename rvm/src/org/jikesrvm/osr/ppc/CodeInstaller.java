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

import static org.jikesrvm.ppc.BaselineConstants.FIRST_FIXED_LOCAL_REGISTER;
import static org.jikesrvm.ppc.BaselineConstants.FIRST_FLOAT_LOCAL_REGISTER;
import static org.jikesrvm.ppc.BaselineConstants.FP;
import static org.jikesrvm.ppc.BaselineConstants.S0;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_FPR;
import static org.jikesrvm.ppc.RegisterConstants.LAST_NONVOLATILE_GPR;
import static org.jikesrvm.ppc.RegisterConstants.LG_INSTRUCTION_WIDTH;
import static org.jikesrvm.ppc.StackframeLayoutConstants.BYTES_IN_STACKSLOT;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_METHOD_ID_OFFSET;
import static org.jikesrvm.ppc.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.runtime.JavaSizeConstants.BYTES_IN_DOUBLE;
import static org.jikesrvm.runtime.UnboxedSizeConstants.BYTES_IN_ADDRESS;

import org.jikesrvm.VM;
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.compilers.baseline.ppc.ArchBaselineCompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.assembler.ppc.Assembler;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.ppc.RegisterConstants.FPR;
import org.jikesrvm.ppc.RegisterConstants.GPR;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * CodeInstaller adjusts registers and return address to make a
 * specialized thread as a normal thread get scheduled. The method
 * prologue ( machine code ) is adjusted to cooperate with the code
 * installer.
 */
public abstract class CodeInstaller {

  /* install the newly compiled instructions. */
  public static boolean install(ExecutionState state, CompiledMethod cm) {

    RVMThread thread = state.getThread();
    byte[] stack = thread.getStack();

    Offset fooFPOffset = state.getFPOffset();

    // we are going to dynamically generate some code recover
    // register values from the stack frame.
    int foomid = Magic.getIntAtOffset(stack, fooFPOffset.plus(STACKFRAME_METHOD_ID_OFFSET));

    CompiledMethod foo = CompiledMethods.getCompiledMethod(foomid);
    int cType = foo.getCompilerType();

    Assembler asm = new Assembler(0, VM.TraceOnStackReplacement);

    /////////////////////////////////////
    ////// recover saved registers.
    /////////////////////////////////////
    if (cType == CompiledMethod.BASELINE) {
      ArchBaselineCompiledMethod bcm = (ArchBaselineCompiledMethod) foo;
      int offset = bcm.getFrameSize();
      for (int i = bcm.getLastFloatStackRegister(); i >= FIRST_FLOAT_LOCAL_REGISTER.value(); --i) {
        offset -= BYTES_IN_DOUBLE;
        asm.emitLFD(FPR.lookup(i), offset, FP);
      }
      for (int i = bcm.getLastFixedStackRegister(); i >= FIRST_FIXED_LOCAL_REGISTER.value(); --i) {
        offset -= BYTES_IN_ADDRESS;
        asm.emitLAddr(GPR.lookup(i), offset, FP);
      }
    } else if (cType == CompiledMethod.OPT) {
      OptCompiledMethod fooOpt = (OptCompiledMethod) foo;

      // foo definitely not save volatile.
      boolean saveVolatile = fooOpt.isSaveVolatile();
      if (VM.VerifyAssertions) {
        VM._assert(!saveVolatile);
      }

      int offset = fooOpt.getUnsignedNonVolatileOffset();

      // recover nonvolatile GPRs
      int firstGPR = fooOpt.getFirstNonVolatileGPR();
      if (firstGPR != -1) {
        for (int i = firstGPR; i <= LAST_NONVOLATILE_GPR.value(); i++) {
          asm.emitLAddr(GPR.lookup(i), offset, FP);
          offset += BYTES_IN_STACKSLOT;
        }
      }

      // recover nonvolatile FPRs
      int firstFPR = fooOpt.getFirstNonVolatileFPR();
      if (firstFPR != -1) {
        for (int i = firstFPR; i <= LAST_NONVOLATILE_FPR.value(); i++) {
          asm.emitLFD(FPR.lookup(i), offset, FP);
          offset += BYTES_IN_DOUBLE;
        }
      }
    }

    if (VM.VerifyAssertions) {
      Object jtocContent = Statics.getSlotContentsAsObject(cm.getOsrJTOCoffset());
      VM._assert(jtocContent == cm.getEntryCodeArray());
    }

    // load address of newInstructions from JTOC
    asm.emitLAddrToc(S0, cm.getOsrJTOCoffset());
    // mov CTR addr
    asm.emitMTCTR(S0);
    // lwz FP, 0(FP)
    asm.emitLAddr(FP, 0, FP);
    // lwz T0, NEXT_INSTR(FP)
    asm.emitLAddr(S0, STACKFRAME_RETURN_ADDRESS_OFFSET.toInt(), FP);
    // mov LR, addr
    asm.emitMTLR(S0);
    // bctr
    asm.emitBCCTR();

    // mark the thread as waiting for on stack replacement.
    thread.isWaitingForOsr = true;
    thread.bridgeInstructions = asm.getMachineCodes();
    thread.fooFPOffset = fooFPOffset;

    Address bridgeaddr = Magic.objectAsAddress(thread.bridgeInstructions);

    Memory.sync(bridgeaddr, thread.bridgeInstructions.length() << LG_INSTRUCTION_WIDTH);

    AOSLogging.logger.logOsrEvent("OSR code installation succeeded");

    return true;
  }
}
