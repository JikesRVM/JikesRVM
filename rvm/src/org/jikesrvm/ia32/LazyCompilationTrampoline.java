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
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.compilers.common.assembler.ia32.Assembler;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;

/**
 * Generate a "trampoline" that jumps to the shared lazy compilation stub.
 * This is then copied into individual TIBs.
 * <p>
 * We do this to enable the optimizing compiler to use ptr equality of
 * target instructions to imply logical (source) equality of target methods.
 * This is used to perform guarded inlining using the "method test."
 * Without per-class lazy compilation trampolines, ptr equality of target
 * instructions does not imply source equality, since both targets may in fact
 * be the globally shared lazy compilation stub.
 */
public abstract class LazyCompilationTrampoline implements BaselineConstants {
  public static final ArchitectureSpecific.CodeArray instructions;

  static {
    Assembler asm = new ArchitectureSpecific.Assembler(0);
    asm.emitJMP_Abs(Magic.getTocPointer().plus(Entrypoints.lazyMethodInvokerMethod.getOffset()));
    instructions = asm.getMachineCodes();
  }
}
