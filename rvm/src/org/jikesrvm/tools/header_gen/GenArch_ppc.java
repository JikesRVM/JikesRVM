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
import org.jikesrvm.ppc.BaselineConstants;
import org.jikesrvm.ppc.RegisterConstants;
import org.jikesrvm.ppc.StackframeLayoutConstants;
import org.jikesrvm.ppc.TrapConstants;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 * Posix version: AIX PPC, Linux PPC, Linux IA32
 */
final class GenArch_ppc extends GenArch {
  @Override
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;
    offset = ArchEntrypoints.registersLRField.getOffset();
    pln("Registers_lr_offset = ", offset);

    p("static const int Constants_JTOC_POINTER               = " + RegisterConstants.JTOC_POINTER + ";\n");
    p("static const int Constants_FRAME_POINTER              = " + RegisterConstants.FRAME_POINTER + ";\n");
    p("static const int Constants_THREAD_REGISTER            = " + RegisterConstants.THREAD_REGISTER + ";\n");
    p("static const int Constants_FIRST_VOLATILE_GPR         = " + RegisterConstants.FIRST_VOLATILE_GPR + ";\n");
    p("static const int Constants_DIVIDE_BY_ZERO_MASK        = " + TrapConstants.DIVIDE_BY_ZERO_MASK + ";\n");
    p("static const int Constants_DIVIDE_BY_ZERO_TRAP        = " + TrapConstants.DIVIDE_BY_ZERO_TRAP + ";\n");
    p("static const int Constants_MUST_IMPLEMENT_MASK        = " + TrapConstants.MUST_IMPLEMENT_MASK + ";\n");
    p("static const int Constants_MUST_IMPLEMENT_TRAP        = " + TrapConstants.MUST_IMPLEMENT_TRAP + ";\n");
    p("static const int Constants_STORE_CHECK_MASK           = " + TrapConstants.STORE_CHECK_MASK + ";\n");
    p("static const int Constants_STORE_CHECK_TRAP           = " + TrapConstants.STORE_CHECK_TRAP + ";\n");
    p("static const int Constants_ARRAY_INDEX_MASK           = " + TrapConstants.ARRAY_INDEX_MASK + ";\n");
    p("static const int Constants_ARRAY_INDEX_TRAP           = " + TrapConstants.ARRAY_INDEX_TRAP + ";\n");
    p("static const int Constants_ARRAY_INDEX_REG_MASK       = " + TrapConstants.ARRAY_INDEX_REG_MASK + ";\n");
    p("static const int Constants_ARRAY_INDEX_REG_SHIFT      = " + TrapConstants.ARRAY_INDEX_REG_SHIFT + ";\n");
    p("static const int Constants_CONSTANT_ARRAY_INDEX_MASK  = " +
      TrapConstants.CONSTANT_ARRAY_INDEX_MASK + ";\n");
    p("static const int Constants_CONSTANT_ARRAY_INDEX_TRAP  = " +
      TrapConstants.CONSTANT_ARRAY_INDEX_TRAP + ";\n");
    p("static const int Constants_CONSTANT_ARRAY_INDEX_INFO  = " +
      TrapConstants.CONSTANT_ARRAY_INDEX_INFO + ";\n");
    p("static const int Constants_WRITE_BUFFER_OVERFLOW_MASK = " +
      TrapConstants.WRITE_BUFFER_OVERFLOW_MASK + ";\n");
    p("static const int Constants_WRITE_BUFFER_OVERFLOW_TRAP = " +
      TrapConstants.WRITE_BUFFER_OVERFLOW_TRAP + ";\n");
    p("static const int Constants_STACK_OVERFLOW_MASK        = " + TrapConstants.STACK_OVERFLOW_MASK + ";\n");
    p("static const int Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP = " +
      TrapConstants.STACK_OVERFLOW_HAVE_FRAME_TRAP + ";\n");
    p("static const int Constants_STACK_OVERFLOW_TRAP        = " + TrapConstants.STACK_OVERFLOW_TRAP + ";\n");
    p("static const int Constants_CHECKCAST_MASK             = " + TrapConstants.CHECKCAST_MASK + ";\n");
    p("static const int Constants_CHECKCAST_TRAP             = " + TrapConstants.CHECKCAST_TRAP + ";\n");
    p("static const int Constants_REGENERATE_MASK            = " + TrapConstants.REGENERATE_MASK + ";\n");
    p("static const int Constants_REGENERATE_TRAP            = " + TrapConstants.REGENERATE_TRAP + ";\n");
    p("static const int Constants_NULLCHECK_MASK             = " + TrapConstants.NULLCHECK_MASK + ";\n");
    p("static const int Constants_NULLCHECK_TRAP             = " + TrapConstants.NULLCHECK_TRAP + ";\n");
    p("static const int Constants_JNI_STACK_TRAP_MASK             = " +
      TrapConstants.JNI_STACK_TRAP_MASK + ";\n");
    p("static const int Constants_JNI_STACK_TRAP             = " + TrapConstants.JNI_STACK_TRAP + ";\n");
    p("static const int Constants_STACKFRAME_NEXT_INSTRUCTION_OFFSET = " +
      StackframeLayoutConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET + ";\n");
    p("static const int Constants_STACKFRAME_ALIGNMENT = " +
      StackframeLayoutConstants.STACKFRAME_ALIGNMENT + " ;\n");
  }

  @Override
  public void emitArchAssemblerDeclarations() {
    if (VM.BuildForOsx) {
      pln("#define FP r" + BaselineConstants.FP);
      pln("#define JTOC r" + BaselineConstants.JTOC);
      pln("#define THREAD_REGISTER r" + BaselineConstants.THREAD_REGISTER);
      pln("#define S0 r" + BaselineConstants.S0);
      pln("#define T0 r" + BaselineConstants.T0);
      pln("#define T1 r" + BaselineConstants.T1);
      pln("#define T2 r" + BaselineConstants.T2);
      pln("#define T3 r" + BaselineConstants.T3);
      pln("#define STACKFRAME_NEXT_INSTRUCTION_OFFSET " +
          StackframeLayoutConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
    } else {
      pln(".set FP," + BaselineConstants.FP);
      pln(".set JTOC," + BaselineConstants.JTOC);
      pln(".set THREAD_REGISTER," + BaselineConstants.THREAD_REGISTER);
      pln(".set S0," + BaselineConstants.S0);
      pln(".set T0," + BaselineConstants.T0);
      pln(".set T1," + BaselineConstants.T1);
      pln(".set T2," + BaselineConstants.T2);
      pln(".set T3," + BaselineConstants.T3);
      pln(".set STACKFRAME_NEXT_INSTRUCTION_OFFSET," + StackframeLayoutConstants.STACKFRAME_NEXT_INSTRUCTION_OFFSET);
      if (!VM.BuildForAix) {
        pln(".set T4," + (BaselineConstants.T3 + 1));
      }
    }
  }
}
