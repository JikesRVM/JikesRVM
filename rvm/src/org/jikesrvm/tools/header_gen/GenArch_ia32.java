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

import org.jikesrvm.ia32.VM_ArchConstants;
import org.jikesrvm.ia32.VM_BaselineConstants;
import org.jikesrvm.ia32.VM_RegisterConstants;
import org.jikesrvm.ia32.VM_StackframeLayoutConstants;
import org.jikesrvm.ia32.VM_TrapConstants;
import org.jikesrvm.runtime.VM_ArchEntrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
final class GenArch_ia32 extends GenArch {
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;

    offset = VM_ArchEntrypoints.registersFPField.getOffset();
    pln("VM_Registers_fp_offset = ", offset);

    p("static const int VM_Constants_EAX                    = " + VM_RegisterConstants.EAX.value() + ";\n");
    p("static const int VM_Constants_ECX                    = " + VM_RegisterConstants.ECX.value() + ";\n");
    p("static const int VM_Constants_EDX                    = " + VM_RegisterConstants.EDX.value() + ";\n");
    p("static const int VM_Constants_EBX                    = " + VM_RegisterConstants.EBX.value() + ";\n");
    p("static const int VM_Constants_ESP                    = " + VM_RegisterConstants.ESP.value() + ";\n");
    p("static const int VM_Constants_EBP                    = " + VM_RegisterConstants.EBP.value() + ";\n");
    p("static const int VM_Constants_ESI                    = " + VM_RegisterConstants.ESI.value() + ";\n");
    p("static const int VM_Constants_EDI                    = " + VM_RegisterConstants.EDI.value() + ";\n");
    p("static const int VM_Constants_STACKFRAME_BODY_OFFSET             = " +
      VM_StackframeLayoutConstants
          .STACKFRAME_BODY_OFFSET +
                                  ";\n");
    p("static const int VM_Constants_STACKFRAME_RETURN_ADDRESS_OFFSET   = " +
      VM_StackframeLayoutConstants
          .STACKFRAME_RETURN_ADDRESS_OFFSET +
                                            ";\n");
    p("static const int VM_Constants_RVM_TRAP_BASE  = " + VM_TrapConstants.RVM_TRAP_BASE + ";\n");

    offset = VM_ArchEntrypoints.framePointerField.getOffset();
    pln("VM_Processor_framePointer_offset = ", offset);
    offset = VM_ArchEntrypoints.arrayIndexTrapParamField.getOffset();
    pln("VM_Processor_arrayIndexTrapParam_offset = ", offset);

    p("static const int VM_ArchConstants_SSE2 = " + (VM_ArchConstants.SSE2_BASE ? "1;\n" : "0;\n"));
  }

  public void emitArchAssemblerDeclarations() {
    p("#define PR %" + VM_BaselineConstants.ESI + ";\n");
  }
}
