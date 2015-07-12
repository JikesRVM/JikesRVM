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

import org.jikesrvm.VM;
import org.jikesrvm.ia32.ArchConstants;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.ia32.RegisterConstants;
import org.jikesrvm.ia32.StackframeLayoutConstants;
import org.jikesrvm.ia32.TrapConstants;
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

    pln("Constants_EAX", RegisterConstants.EAX.value());
    pln("Constants_ECX", RegisterConstants.ECX.value());
    pln("Constants_EDX", RegisterConstants.EDX.value());
    pln("Constants_EBX", RegisterConstants.EBX.value());
    pln("Constants_ESP", RegisterConstants.ESP.value());
    pln("Constants_EBP", RegisterConstants.EBP.value());
    pln("Constants_ESI", RegisterConstants.ESI.value());
    pln("Constants_EDI", RegisterConstants.EDI.value());
    if (VM.BuildFor64Addr) {
      pln("Constants_R8", RegisterConstants.R8.value());
      pln("Constants_R9", RegisterConstants.R9.value());
      pln("Constants_R10", RegisterConstants.R10.value());
      pln("Constants_R11", RegisterConstants.R11.value());
      pln("Constants_R12", RegisterConstants.R12.value());
      pln("Constants_R13", RegisterConstants.R13.value());
      pln("Constants_R14", RegisterConstants.R14.value());
      pln("Constants_R15", RegisterConstants.R15.value());
    }
    pln("Constants_STACKFRAME_BODY_OFFSET", StackframeLayoutConstants.STACKFRAME_BODY_OFFSET);
    pln("Constants_STACKFRAME_RETURN_ADDRESS_OFFSET", StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET);
    pln("Constants_RVM_TRAP_BASE", TrapConstants.RVM_TRAP_BASE);

    offset = ArchEntrypoints.framePointerField.getOffset();
    pln("Thread_framePointer_offset", offset);
    offset = ArchEntrypoints.arrayIndexTrapParamField.getOffset();
    pln("Thread_arrayIndexTrapParam_offset", offset);

    pln("ArchConstants_SSE2", (ArchConstants.SSE2_BASE ? 1 : 0));
  }

  @Override
  public void emitArchAssemblerDeclarations() {
    if (BaselineConstants.TR != BaselineConstants.ESI) {
      throw new Error("Unexpected TR value");
    }
    p("#define TR %ESI\n");
  }
}
