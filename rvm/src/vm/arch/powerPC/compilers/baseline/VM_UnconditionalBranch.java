/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *
 * @author Bowen Alpern
 */

class VM_UnconditionalBranch extends VM_ForwardReference {

  VM_UnconditionalBranch (int source) {
    super(source);
  }

  VM_UnconditionalBranch (int source, int btarget) {
    super(source, btarget);
  }

  void resolve (VM_MachineCode code, int target) {
    if (VM.TraceAssembler) 
      System.out.print(" <- " + VM_Assembler.hex(sourceMachinecodeIndex << 2));
    int delta = target - sourceMachinecodeIndex;
    INSTRUCTION instr = code.getInstruction(sourceMachinecodeIndex);
    if (VM.VerifyAssertions) VM.assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
    instr |= (delta<<2);
    code.putInstruction(sourceMachinecodeIndex, instr);
  }

}
