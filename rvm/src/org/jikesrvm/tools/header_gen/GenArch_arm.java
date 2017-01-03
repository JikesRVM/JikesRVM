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

import static org.jikesrvm.arm.RegisterConstants.FIRST_VOLATILE_GPR;
import static org.jikesrvm.arm.RegisterConstants.FP;
import static org.jikesrvm.arm.RegisterConstants.JTOC;
import static org.jikesrvm.arm.RegisterConstants.TR;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_END_OF_FRAME_OFFSET;
import static org.jikesrvm.arm.StackframeLayoutConstants.STACKFRAME_PARAMETER_OFFSET;
import static org.jikesrvm.arm.TrapConstants.ARRAY_INDEX_TRAP;
import static org.jikesrvm.arm.TrapConstants.DIVIDE_ZERO_TRAP;
import static org.jikesrvm.arm.TrapConstants.STACK_OVERFLOW_TRAP;
import static org.jikesrvm.arm.TrapConstants.JNI_STACK_TRAP;
import static org.jikesrvm.arm.TrapConstants.NULLCHECK_TRAP;
import static org.jikesrvm.arm.TrapConstants.INTERFACE_TRAP;
import static org.jikesrvm.arm.TrapConstants.CHECKCAST_TRAP;
import static org.jikesrvm.arm.TrapConstants.IGNORE_COND;
import static org.jikesrvm.arm.TrapConstants.ARRAY_INDEX_REG_MASK;
import static org.jikesrvm.arm.TrapConstants.ARRAY_INDEX_REG_SHIFT;

import org.jikesrvm.runtime.ArchEntrypoints;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.arm.RegisterConstants.GPR;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C.
 */
final class GenArch_arm extends GenArch {

  static void pln(String s, GPR gp) {
    out.print("#define " + s + " 0x" + Integer.toHexString(gp.value()) + "\n");
  }

  @Override
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;
    offset = ArchEntrypoints.registersLRField.getOffset();
    pln("Registers_lr_offset", offset);

    pln("Constants_JTOC_POINTER", JTOC);
    pln("Constants_FRAME_POINTER", FP);
    pln("Constants_THREAD_REGISTER", TR);
    pln("Constants_FIRST_VOLATILE_GPR", FIRST_VOLATILE_GPR);

    pln("Constants_ARRAY_INDEX_TRAP", ARRAY_INDEX_TRAP);
    pln("Constants_DIVIDE_ZERO_TRAP", DIVIDE_ZERO_TRAP);
    pln("Constants_STACK_OVERFLOW_TRAP", STACK_OVERFLOW_TRAP);
    pln("Constants_JNI_STACK_TRAP", JNI_STACK_TRAP);
    pln("Constants_NULLCHECK_TRAP", NULLCHECK_TRAP);
    pln("Constants_INTERFACE_TRAP", INTERFACE_TRAP);
    pln("Constants_CHECKCAST_TRAP", CHECKCAST_TRAP);
    pln("Constants_IGNORE_COND", IGNORE_COND);

    pln("Constants_ARRAY_INDEX_REG_MASK", ARRAY_INDEX_REG_MASK);
    pln("Constants_ARRAY_INDEX_REG_SHIFT", ARRAY_INDEX_REG_SHIFT);

    pln("Constants_STACKFRAME_RETURN_ADDRESS_OFFSET", STACKFRAME_RETURN_ADDRESS_OFFSET);
    pln("Constants_STACKFRAME_END_OF_FRAME_OFFSET", STACKFRAME_END_OF_FRAME_OFFSET);
    pln("Constants_STACKFRAME_PARAMETER_OFFSET", STACKFRAME_PARAMETER_OFFSET);
  }

  @Override
  public void emitArchAssemblerDeclarations() {
    // Nothing to do
  }
}
