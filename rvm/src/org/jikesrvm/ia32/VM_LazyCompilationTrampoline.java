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
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.compilers.common.assembler.ia32.VM_Assembler;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;

/**
 * Generate a "trampoline" that jumps to the shared lazy compilation stub.
 * This is then copied into individual TIBs.
 *
 * We do this to enable the optimizing compiler to use ptr equality of
 * target instructions to imply logical (source) equality of target methods.
 * This is used to perform guarded inlining using the "method test."
 * Without per-class lazy compilation trampolines, ptr equality of target
 * instructions does not imply source equality, since both targets may in fact
 * be the globally shared lazy compilation stub.
 */
public abstract class VM_LazyCompilationTrampoline implements VM_BaselineConstants {
  public static ArchitectureSpecific.VM_CodeArray instructions;

  static {
    VM_Assembler asm = new ArchitectureSpecific.VM_Assembler(0);
    asm.emitJMP_Abs(VM_Magic.getTocPointer().plus(VM_Entrypoints.lazyMethodInvokerMethod.getOffset()));
    instructions = asm.getMachineCodes();
  }
}
