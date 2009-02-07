/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.tools.header_gen;

import org.jikesrvm.ia32.ArchConstants;
import org.jikesrvm.ia32.BaselineConstants;
import org.jikesrvm.ia32.RegisterConstants;
import org.jikesrvm.ia32.StackframeLayoutConstants;
import org.jikesrvm.ia32.TrapConstants;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
final class GenArch_ia32 extends GenArch {
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;

    offset = ArchEntrypoints.registersFPField.getOffset();
    pln("Registers_fp_offset = ", offset);

    p("static const int Constants_EAX                    = " + RegisterConstants.EAX.value() + ";\n");
    p("static const int Constants_ECX                    = " + RegisterConstants.ECX.value() + ";\n");
    p("static const int Constants_EDX                    = " + RegisterConstants.EDX.value() + ";\n");
    p("static const int Constants_EBX                    = " + RegisterConstants.EBX.value() + ";\n");
    p("static const int Constants_ESP                    = " + RegisterConstants.ESP.value() + ";\n");
    p("static const int Constants_EBP                    = " + RegisterConstants.EBP.value() + ";\n");
    p("static const int Constants_ESI                    = " + RegisterConstants.ESI.value() + ";\n");
    p("static const int Constants_EDI                    = " + RegisterConstants.EDI.value() + ";\n");
    p("static const int Constants_STACKFRAME_BODY_OFFSET             = " +
      StackframeLayoutConstants.STACKFRAME_BODY_OFFSET + ";\n");
    p("static const int Constants_STACKFRAME_RETURN_ADDRESS_OFFSET   = " +
      StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET + ";\n");
    p("static const int Constants_RVM_TRAP_BASE  = " + TrapConstants.RVM_TRAP_BASE + ";\n");

    offset = ArchEntrypoints.framePointerField.getOffset();
    pln("Thread_framePointer_offset = ", offset);
    offset = ArchEntrypoints.arrayIndexTrapParamField.getOffset();
    pln("Thread_arrayIndexTrapParam_offset = ", offset);

    p("static const int ArchConstants_SSE2 = " + (ArchConstants.SSE2_BASE ? "1;\n" : "0;\n"));
  }

  public void emitArchAssemblerDeclarations() {
    if (BaselineConstants.TR != BaselineConstants.ESI) {
      throw new Error("Unexpected TR value");
    }
    p("#define TR %ESI;\n");
  }
}
