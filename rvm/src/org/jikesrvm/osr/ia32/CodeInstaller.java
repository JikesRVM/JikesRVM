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
import org.jikesrvm.adaptive.util.AOSLogging;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.osr.ExecutionState;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

/**
 * CodeInstaller generates a glue code which recovers registers and
 * from the stack frames and branch to the newly compiled method instructions.
 * The glue code is installed right before returning to the threading method
 * by PostThreadSwitch
 */
public abstract class CodeInstaller implements BaselineConstants {

  public static boolean install(ExecutionState state, CompiledMethod cm) {
    RVMThread thread = state.getThread();
    byte[] stack = thread.getStack();

    Offset tsfromFPOffset = state.getTSFPOffset();
    Offset fooFPOffset = state.getFPOffset();

    int foomid = Magic.getIntAtOffset(stack, fooFPOffset.plus(STACKFRAME_METHOD_ID_OFFSET));

    CompiledMethod foo = CompiledMethods.getCompiledMethod(foomid);
    int cType = foo.getCompilerType();

    int SW_WIDTH = 4;

    // this offset is used to adjust SP to FP right after return
    // from a call. 4 bytes for return address and
    // 4 bytes for saved FP of tsfrom.
    Offset sp2fpOffset = fooFPOffset.minus(tsfromFPOffset).minus(2 * SW_WIDTH);

    // should given an estimated length, and print the instructions
    // for debugging
    Assembler asm = new ArchitectureSpecific.Assembler(50, VM.TraceOnStackReplacement);

    // 1. generate bridge instructions to recover saved registers
    if (cType == CompiledMethod.BASELINE) {

//        asm.emitINT_Imm(3);  // break here for debugging

      // unwind stack pointer, SP is FP now
      asm.emitADD_Reg_Imm(SP, sp2fpOffset.toInt());

      asm.emitMOV_Reg_Abs(S0, Magic.getTocPointer().plus(cm.getOsrJTOCoffset()));

      // restore saved EDI
      asm.emitMOV_Reg_RegDisp(EDI, SP, EDI_SAVE_OFFSET);
      // restore saved EBX
      asm.emitMOV_Reg_RegDisp(EBX, SP, EBX_SAVE_OFFSET);
      // restore frame pointer
      asm.emitPOP_RegDisp(TR, ArchEntrypoints.framePointerField.getOffset());
      // do not pop return address and parameters,
      // we make a faked call to newly compiled method
      asm.emitJMP_Reg(S0);
    } else if (cType == CompiledMethod.OPT) {
      ///////////////////////////////////////////////////
      // recover saved registers from foo's stack frame
      ///////////////////////////////////////////////////
      OptCompiledMethod fooOpt = (OptCompiledMethod) foo;

      // foo definitely not save volatile
      boolean saveVolatile = fooOpt.isSaveVolatile();
      if (VM.VerifyAssertions) {
        VM._assert(!saveVolatile);
      }

      // assume SP is on foo's stack frame,
      int firstNonVolatile = fooOpt.getFirstNonVolatileGPR();
      int nonVolatiles = fooOpt.getNumberOfNonvolatileGPRs();
      int nonVolatileOffset = fooOpt.getUnsignedNonVolatileOffset();

      for (int i = firstNonVolatile; i < firstNonVolatile + nonVolatiles; i++) {
        asm.emitMOV_Reg_RegDisp(NONVOLATILE_GPRS[i], SP, sp2fpOffset.minus(nonVolatileOffset));
        nonVolatileOffset += SW_WIDTH;
      }
      // adjust SP to frame pointer
      asm.emitADD_Reg_Imm(SP, sp2fpOffset.toInt());
      // restore frame pointer
      asm.emitPOP_RegDisp(TR, ArchEntrypoints.framePointerField.getOffset());

      // branch to the newly compiled instructions
      asm.emitJMP_Abs(Magic.getTocPointer().plus(cm.getOsrJTOCoffset()));
    }

    if (VM.TraceOnStackReplacement) {
      VM.sysWrite("new CM instr addr ");
      VM.sysWriteHex(Statics.getSlotContentsAsInt(cm.getOsrJTOCoffset()));
      VM.sysWriteln();
      VM.sysWrite("JTOC register ");
      VM.sysWriteHex(Magic.getTocPointer());
      VM.sysWriteln();
      VM.sysWrite("Thread register ");
      VM.sysWriteHex(Magic.objectAsAddress(Magic.getThreadRegister()));
      VM.sysWriteln();

      VM.sysWriteln("tsfromFPOffset ", tsfromFPOffset);
      VM.sysWriteln("fooFPOffset ", fooFPOffset);
      VM.sysWriteln("SP + ", sp2fpOffset.plus(4));
    }

    // 3. set thread flags
    thread.isWaitingForOsr = true;
    thread.bridgeInstructions = asm.getMachineCodes();
    thread.fooFPOffset = fooFPOffset;
    thread.tsFPOffset = tsfromFPOffset;

    Address bridgeaddr = Magic.objectAsAddress(thread.bridgeInstructions);

    Memory.sync(bridgeaddr, thread.bridgeInstructions.length() << LG_INSTRUCTION_WIDTH);

    AOSLogging.logger.logOsrEvent("OSR code installation succeeded");

    return true;
  }
}
