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

import static org.jikesrvm.ppc.TrapConstants.*;

import org.jikesrvm.ppc.RegisterConstants;
import org.jikesrvm.ppc.StackframeLayoutConstants;
import org.jikesrvm.runtime.ArchEntrypoints;
import org.vmmagic.unboxed.Offset;

/**
 * Emit the architecture-specific part of a header file containing declarations
 * required to access VM data structures from C++.
 */
final class GenArch_ppc extends GenArch {
  @Override
  public void emitArchVirtualMachineDeclarations() {
    Offset offset;
    offset = ArchEntrypoints.registersLRField.getOffset();
    pln("Registers_lr_offset", offset);

    pln("Constants_JTOC_POINTER", RegisterConstants.JTOC_POINTER);
    pln("Constants_FRAME_POINTER", RegisterConstants.FRAME_POINTER);
    pln("Constants_THREAD_REGISTER", RegisterConstants.THREAD_REGISTER);
    pln("Constants_FIRST_VOLATILE_GPR", RegisterConstants.FIRST_VOLATILE_GPR);
    pln("Constants_DIVIDE_BY_ZERO_MASK", DIVIDE_BY_ZERO_MASK);
    pln("Constants_DIVIDE_BY_ZERO_TRAP", DIVIDE_BY_ZERO_TRAP);
    pln("Constants_MUST_IMPLEMENT_MASK", MUST_IMPLEMENT_MASK);
    pln("Constants_MUST_IMPLEMENT_TRAP", MUST_IMPLEMENT_TRAP);
    pln("Constants_STORE_CHECK_MASK", STORE_CHECK_MASK);
    pln("Constants_STORE_CHECK_TRAP", STORE_CHECK_TRAP);
    pln("Constants_ARRAY_INDEX_MASK", ARRAY_INDEX_MASK);
    pln("Constants_ARRAY_INDEX_TRAP", ARRAY_INDEX_TRAP);
    pln("Constants_ARRAY_INDEX_REG_MASK", ARRAY_INDEX_REG_MASK);
    pln("Constants_ARRAY_INDEX_REG_SHIFT", ARRAY_INDEX_REG_SHIFT);
    pln("Constants_CONSTANT_ARRAY_INDEX_MASK", CONSTANT_ARRAY_INDEX_MASK);
    pln("Constants_CONSTANT_ARRAY_INDEX_TRAP", CONSTANT_ARRAY_INDEX_TRAP);
    pln("Constants_CONSTANT_ARRAY_INDEX_INFO", CONSTANT_ARRAY_INDEX_INFO);
    pln("Constants_WRITE_BUFFER_OVERFLOW_MASK", WRITE_BUFFER_OVERFLOW_MASK);
    pln("Constants_WRITE_BUFFER_OVERFLOW_TRAP", WRITE_BUFFER_OVERFLOW_TRAP);
    pln("Constants_STACK_OVERFLOW_MASK", STACK_OVERFLOW_MASK);
    pln("Constants_STACK_OVERFLOW_HAVE_FRAME_TRAP", STACK_OVERFLOW_HAVE_FRAME_TRAP);
    pln("Constants_STACK_OVERFLOW_TRAP", STACK_OVERFLOW_TRAP);
    pln("Constants_STACK_OVERFLOW_TRAP_INFO_SET_HAVE_FRAME", STACK_OVERFLOW_TRAP_INFO_SET_HAVE_FRAME);
    pln("Constants_CHECKCAST_MASK", CHECKCAST_MASK);
    pln("Constants_CHECKCAST_TRAP", CHECKCAST_TRAP);
    pln("Constants_REGENERATE_MASK", REGENERATE_MASK);
    pln("Constants_REGENERATE_TRAP", REGENERATE_TRAP);
    pln("Constants_NULLCHECK_MASK", NULLCHECK_MASK);
    pln("Constants_NULLCHECK_TRAP", NULLCHECK_TRAP);
    pln("Constants_JNI_STACK_TRAP_MASK", JNI_STACK_TRAP_MASK);
    pln("Constants_JNI_STACK_TRAP", JNI_STACK_TRAP);
    pln("Constants_STACKFRAME_RETURN_ADDRESS_OFFSET", StackframeLayoutConstants.STACKFRAME_RETURN_ADDRESS_OFFSET);
    pln("Constants_STACKFRAME_ALIGNMENT", StackframeLayoutConstants.STACKFRAME_ALIGNMENT);
  }

  @Override
  public void emitArchAssemblerDeclarations() {
    // Nothing to do
  }
}
