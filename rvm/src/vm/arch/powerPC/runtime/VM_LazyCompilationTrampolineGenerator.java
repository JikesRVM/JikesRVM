/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

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
public class VM_LazyCompilationTrampolineGenerator implements VM_BaselineConstants {

  /** 
   * Generate a new lazy compilation trampoline. 
   */
  public static INSTRUCTION[] getTrampoline () {
    VM_Assembler asm = new VM_Assembler(0);
    asm.emitLWZtoc (S0, VM_Entrypoints.lazyMethodInvokerMethod.getOffset());
    asm.emitMTCTR(S0);
    asm.emitBCCTR ();
    return asm.makeMachineCode().getInstructions();
  }
}
