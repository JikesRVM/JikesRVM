/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.ppc;

import com.ibm.jikesrvm.VM_Entrypoints;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_Assembler;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_BaselineConstants;
import com.ibm.jikesrvm.ArchitectureSpecific.VM_CodeArray;

/**
 * Generate a "trampoline" that jumps to the shared lazy compilation stub.
 * We do this to enable the optimizing compiler to use ptr equality of
 * target instructions to imply logical (source) equality of target methods.
 * This is used to perform guarded inlining using the "method test."
 * Without per-method lazy compilation trampolines, ptr equality of target
 * instructions does not imply source equality, since both targets may in fact
 * be the globally shared lazy compilation stub.
 * 
 * @author Dave Grove
 */
public abstract class VM_LazyCompilationTrampolineGenerator implements VM_BaselineConstants {

  /** 
   * Generate a new lazy compilation trampoline. 
   */
  public static VM_CodeArray getTrampoline () {
    VM_Assembler asm = new VM_Assembler(0);
    asm.emitLAddrToc (S0, VM_Entrypoints.lazyMethodInvokerMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitBCCTR ();
    return asm.makeMachineCode().getInstructions();
  }
}
