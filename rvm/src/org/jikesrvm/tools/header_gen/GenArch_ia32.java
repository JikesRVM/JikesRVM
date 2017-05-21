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
package org.jikesrvm.tools.header_gen;

import static org.jikesrvm.ia32.ArchConstants.SSE2_BASE;
import static org.jikesrvm.ia32.BaselineConstants.TR;
import static org.jikesrvm.ia32.RegisterConstants.EAX;
import static org.jikesrvm.ia32.RegisterConstants.EBP;
import static org.jikesrvm.ia32.RegisterConstants.EBX;
import static org.jikesrvm.ia32.RegisterConstants.ECX;
import static org.jikesrvm.ia32.RegisterConstants.EDI;
import static org.jikesrvm.ia32.RegisterConstants.EDX;
import static org.jikesrvm.ia32.RegisterConstants.ESI;
import static org.jikesrvm.ia32.RegisterConstants.ESP;
import static org.jikesrvm.ia32.RegisterConstants.R10;
import static org.jikesrvm.ia32.RegisterConstants.R11;
import static org.jikesrvm.ia32.RegisterConstants.R12;
import static org.jikesrvm.ia32.RegisterConstants.R13;
import static org.jikesrvm.ia32.RegisterConstants.R14;
import static org.jikesrvm.ia32.RegisterConstants.R15;
import static org.jikesrvm.ia32.RegisterConstants.R8;
import static org.jikesrvm.ia32.RegisterConstants.R9;
import static org.jikesrvm.ia32.StackframeLayoutConstants.RED_ZONE_SIZE;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_BODY_OFFSET;
import static org.jikesrvm.ia32.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.ia32.TrapConstants.RVM_TRAP_BASE;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.regalloc.ia32.StackManager;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C.
 */
final class GenArch_ia32 extends GenArch {
  @Override
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;

    offset = ArchEntrypoints.registersFPField.getOffset();
    pln("Registers_fp_offset", offset);

    pln("Constants_EAX", EAX.value());
    pln("Constants_ECX", ECX.value());
    pln("Constants_EDX", EDX.value());
    pln("Constants_EBX", EBX.value());
    pln("Constants_ESP", ESP.value());
    pln("Constants_EBP", EBP.value());
    pln("Constants_ESI", ESI.value());
    pln("Constants_EDI", EDI.value());
    if (VM.BuildFor64Addr) {
      pln("Constants_R8", R8.value());
      pln("Constants_R9", R9.value());
      pln("Constants_R10", R10.value());
      pln("Constants_R11", R11.value());
      pln("Constants_R12", R12.value());
      pln("Constants_R13", R13.value());
      pln("Constants_R14", R14.value());
      pln("Constants_R15", R15.value());
    }
    pln("Constants_STACKFRAME_BODY_OFFSET", STACKFRAME_BODY_OFFSET);
    pln("Constants_STACKFRAME_RETURN_ADDRESS_OFFSET", STACKFRAME_RETURN_ADDRESS_OFFSET);
    pln("Constants_RVM_TRAP_BASE", RVM_TRAP_BASE);
    pln("Constants_RED_ZONE_SIZE", RED_ZONE_SIZE);
    if (VM.BuildForOptCompiler) {
      pln("Constants_MAX_DIFFERENCE_TO_STACK_LIMIT", StackManager.MAX_DIFFERENCE_TO_STACK_LIMIT);
    } else {
      // The baseline compiler always checks for stack overflow before
      // creating the frame, so it's not necessary to allow any overflow
      // into the guard region of the stack.
      pln("Constants_MAX_DIFFERENCE_TO_STACK_LIMIT", 0);
    }

    offset = ArchEntrypoints.framePointerField.getOffset();
    pln("Thread_framePointer_offset", offset);
    offset = ArchEntrypoints.arrayIndexTrapParamField.getOffset();
    pln("Thread_arrayIndexTrapParam_offset", offset);

    pln("ArchConstants_SSE2", (SSE2_BASE ? 1 : 0));
  }

  @Override
  public void emitArchAssemblerDeclarations() {
    if (TR != ESI) {
      throw new Error("Unexpected TR value");
    }
    pln("#define TR %ESI");
  }
}
