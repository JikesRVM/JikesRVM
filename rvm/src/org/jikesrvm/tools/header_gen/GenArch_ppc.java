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

import org.jikesrvm.VM;
import org.jikesrvm.ppc.VM_BaselineConstants;
import org.jikesrvm.ppc.VM_RegisterConstants;
import org.jikesrvm.ppc.VM_StackframeLayoutConstants;
import org.jikesrvm.ppc.VM_TrapConstants;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
final class GenArch_ppc extends GenArch {
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;
    offset = VM_Entrypoints.registersLRField.getOffset();
    pln("VM_Registers_lr_offset = ", offset);

    p("static const int VM_Constants_JTOC_POINTER               = " + VM_RegisterConstants.JTOC_POINTER + ";\n");
    p("static const int VM_Constants_FRAME_POINTER              = " + VM_RegisterConstants.FRAME_POINTER + ";\n");
    p("static const int VM_Constants_PROCESSOR_REGISTER         = " + VM_RegisterConstants.PROCESSOR_REGISTER + ";\n");
    p("static const int VM_Constants_FIRST_VOLATILE_GPR         = " + VM_RegisterConstants.FIRST_VOLATILE_GPR + ";\n");
    p("static const int VM_Constants_DIVIDE_BY_ZERO_MASK        = " + VM_TrapConstants.DIVIDE_BY_ZERO_MASK + ";\n");
    p("static const int VM_Constants_DIVIDE_BY_ZERO_TRAP        = " + VM_TrapConstants.DIVIDE_BY_ZERO_TRAP + ";\n");
    p("static const int VM_Constants_MUST_IMPLEMENT_MASK        = " + VM_TrapConstants.MUST_IMPLEMENT_MASK + ";\n");
    p("static const int VM_Constants_MUST_IMPLEMENT_TRAP        = " + VM_TrapConstants.MUST_IMPLEMENT_TRAP + ";\n");
    p("static const int VM_Constants_STORE_CHECK_MASK           = " + VM_TrapConstants.STORE_CHECK_MASK + ";\n");
    p("static const int VM_Constants_STORE_CHECK_TRAP           = " + VM_TrapConstants.STORE_CHECK_TRAP + ";\n");
    p("static const int VM_Constants_ARRAY_INDEX_MASK           = " + VM_TrapConstants.ARRAY_INDEX_MASK + ";\n");
    p("static const int VM_Constants_ARRAY_INDEX_TRAP           = " + VM_TrapConstants.ARRAY_INDEX_TRAP + ";\n");
    p("static const int VM_Constants_ARRAY_INDEX_REG_MASK       = " + VM_TrapConstants.ARRAY_INDEX_REG_MASK + ";\n");
    p("static const int VM_Constants_ARRAY_INDEX_REG_SHIFT      = " + VM_TrapConstants.ARRAY_INDEX_REG_SHIFT + ";\n");
    p("static const int VM_Constants_CONSTANT_ARRAY_INDEX_MASK  = " +
      VM_TrapConstants
          .CONSTANT_ARRAY_INDEX_MASK +
                                     ";\n");
    p("static const int VM_Constants_CONSTANT_ARRAY_INDEX_TRAP  = " +
      VM_TrapConstants
          .CONSTANT_ARRAY_INDEX_TRAP +
                                     ";\n");
    p("static const int VM_Constants_CONSTANT_ARRAY_INDEX_INFO  = " +
      VM_TrapConstants
          .CONSTANT_ARRAY_INDEX_INFO +
                                     ";\n");
    p("static const int VM_Constants_WRITE_BUFFER_OVERFLOW_MASK = " +
      VM_TrapConstants
          .WRITE_BUFFER_OVERFLOW_MASK +
                                      ";\n");
    p("static const int VM_Constants_WRITE_BUFFER_OVERFLOW_TRAP = " +
      VM_TrapConstants
          .WRITE_BUFFER_OVERFLOW_TRAP +
                                      ";\n");
    p("static const int VM_Constants_STACK_OVERFLOW_MASK        = " + VM_TrapConstants.STACK_OVERFLOW_MASK + ";\n");
    p("static const int VM_Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP = " +
      VM_TrapConstants
          .STACK_OVERFLOW_HAVE_FRAME_TRAP +
                                          ";\n");
    p("static const int VM_Constants_STACK_OVERFLOW_TRAP        = " + VM_TrapConstants.STACK_OVERFLOW_TRAP + ";\n");
    p("static const int VM_Constants_CHECKCAST_MASK             = " + VM_TrapConstants.CHECKCAST_MASK + ";\n");
    p("static const int VM_Constants_CHECKCAST_TRAP             = " + VM_TrapConstants.CHECKCAST_TRAP + ";\n");
    p("static const int VM_Constants_REGENERATE_MASK            = " + VM_TrapConstants.REGENERATE_MASK + ";\n");
    p("static const int VM_Constants_REGENERATE_TRAP            = " + VM_TrapConstants.REGENERATE_TRAP + ";\n");
    p("static const int VM_Constants_NULLCHECK_MASK             = " + VM_TrapConstants.NULLCHECK_MASK + ";\n");
    p("static const int VM_Constants_NULLCHECK_TRAP             = " + VM_TrapConstants.NULLCHECK_TRAP + ";\n");
    p("static const int VM_Constants_JNI_STACK_TRAP_MASK             = " +
      VM_TrapConstants
          .JNI_STACK_TRAP_MASK +
                               ";\n");
    p("static const int VM_Constants_JNI_STACK_TRAP             = " + VM_TrapConstants.JNI_STACK_TRAP + ";\n");
    p("static const int VM_Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET = " +
      VM_StackframeLayoutConstants
          .STACKFRAME_NEXT_INSTRUCTION_OFFSET +
                                              ";\n");
    p("static const int VM_Constants_STACKFRAME_ALIGNMENT = " +
      VM_StackframeLayoutConstants
          .STACKFRAME_ALIGNMENT +
                                " ;\n");
  }

  public void emitArchAssemblerDeclarations() {
    if (VM.BuildForOsx) {
      pln("#define FP r" + VM_BaselineConstants.FP);
      pln("#define JTOC r" + VM_BaselineConstants.JTOC);
      pln("#define PROCESSOR_REGISTER r" + VM_BaselineConstants.PROCESSOR_REGISTER);
      pln("#define S0 r" + VM_BaselineConstants.S0);
      pln("#define T0 r" + VM_BaselineConstants.T0);
      pln("#define T1 r" + VM_BaselineConstants.T1);
      pln("#define T2 r" + VM_BaselineConstants.T2);
      pln("#define T3 r" + VM_BaselineConstants.T3);
      pln("#define STACKFRAME_NEXT_INSTRUCTION_OFFSET " + VM_StackframeLayoutConstants
          .STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    } else {
      pln(".set FP," + VM_BaselineConstants.FP);
      pln(".set JTOC," + VM_BaselineConstants.JTOC);
      pln(".set PROCESSOR_REGISTER," + VM_BaselineConstants.PROCESSOR_REGISTER);
      pln(".set S0," + VM_BaselineConstants.S0);
      pln(".set T0," + VM_BaselineConstants.T0);
      pln(".set T1," + VM_BaselineConstants.T1);
      pln(".set T2," + VM_BaselineConstants.T2);
      pln(".set T3," + VM_BaselineConstants.T3);
      pln(".set STACKFRAME_NEXT_INSTRUCTION_OFFSET," + VM_StackframeLayoutConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (!VM.BuildForAix) {
        pln(".set T4," + (VM_BaselineConstants.T3 + 1));
      }
    }
  }
}
