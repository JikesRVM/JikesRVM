/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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
class VM_LazyCompilationTrampolineGenerator implements VM_BaselineConstants {

  /** Generate a new lazy compilation trampoline. */
  static INSTRUCTION[] getTrampoline () {
    int offset = ((VM_Method)VM.getMember("LVM_DynamicLinker;", "lazyMethodInvoker", "()V")).getOffset();
    INSTRUCTION[] stub;
    if (offset < 0xFFFF) {
      stub = new INSTRUCTION[3];
      stub[0] = VM_Assembler.L     (S0, offset, JTOC);
      stub[1] = VM_Assembler.MTCTR (S0);
      stub[2] = VM_Assembler.BCTR  ();
    } else {
      stub = new INSTRUCTION[4];
      stub[0] = VM_Assembler.CAU   (S0, JTOC, (offset>>16)+1);
      stub[1] = VM_Assembler.L     (S0, offset|0xFFFF0000, S0);
      stub[2] = VM_Assembler.MTCTR (S0);
      stub[3] = VM_Assembler.BCTR  ();
    }
    if (VM.runningVM)    // synchronize icache with generated machine code that was written through dcache
      VM_Memory.sync(VM_Magic.objectAsAddress(stub), 4 << LG_INSTRUCTION_WIDTH); 
    return stub;
  }
}
