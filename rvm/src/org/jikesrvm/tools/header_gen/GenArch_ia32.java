/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2001,2002, 2003
 */
package org.jikesrvm.tools.header_gen;

import org.jikesrvm.ia32.VM_BaselineConstants;
import org.jikesrvm.ia32.VM_RegisterConstants;
import org.jikesrvm.ia32.VM_StackframeLayoutConstants;
import org.jikesrvm.ia32.VM_TrapConstants;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
final class GenArch_ia32 extends GenArch {
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;

    offset = VM_Entrypoints.registersFPField.getOffset();
    pln("VM_Registers_fp_offset = ", offset);

    p("static const int VM_Constants_EAX                    = " + VM_RegisterConstants.EAX + ";\n");
    p("static const int VM_Constants_ECX                    = " + VM_RegisterConstants.ECX + ";\n");
    p("static const int VM_Constants_EDX                    = " + VM_RegisterConstants.EDX + ";\n");
    p("static const int VM_Constants_EBX                    = " + VM_RegisterConstants.EBX + ";\n");
    p("static const int VM_Constants_ESP                    = " + VM_RegisterConstants.ESP + ";\n");
    p("static const int VM_Constants_EBP                    = " + VM_RegisterConstants.EBP + ";\n");
    p("static const int VM_Constants_ESI                    = " + VM_RegisterConstants.ESI + ";\n");
    p("static const int VM_Constants_EDI                    = " + VM_RegisterConstants.EDI + ";\n");
    p("static const int VM_Constants_STACKFRAME_BODY_OFFSET             = " +
      VM_StackframeLayoutConstants
          .STACKFRAME_BODY_OFFSET +
                                  ";\n");
    p("static const int VM_Constants_STACKFRAME_RETURN_ADDRESS_OFFSET   = " +
      VM_StackframeLayoutConstants
          .STACKFRAME_RETURN_ADDRESS_OFFSET +
                                            ";\n");
    p("static const int VM_Constants_RVM_TRAP_BASE  = " + VM_TrapConstants.RVM_TRAP_BASE + ";\n");

    offset = VM_Entrypoints.framePointerField.getOffset();
    pln("VM_Processor_framePointer_offset = ", offset);
    offset = VM_Entrypoints.jtocField.getOffset();
    pln("VM_Processor_jtoc_offset = ", offset);
    offset = VM_Entrypoints.arrayIndexTrapParamField.getOffset();
    pln("VM_Processor_arrayIndexTrapParam_offset = ", offset);
  }

  public void emitArchAssemblerDeclarations() {
    p("#define JTOC %" + VM_RegisterConstants.GPR_NAMES[VM_BaselineConstants.JTOC] + ";\n");
    p("#define PR %" + VM_RegisterConstants.GPR_NAMES[VM_BaselineConstants.ESI] + ";\n");
  }
}
