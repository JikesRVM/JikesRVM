/*
 * (C) Copyright IBM Corp. 2001
 */

class VM_ConditionalBranch extends VM_ForwardReference {

  VM_ConditionalBranch (int source) {
    super(source);
  }

  VM_ConditionalBranch (int source, int btarget) {
    super(source, btarget);
  }

  void resolve (VM_MachineCode code, int target) {
    if (VM.TraceAssembler) 
      System.out.print(" << " + VM_Assembler.hex(sourceMachinecodeIndex << 2));
    int delta = target - sourceMachinecodeIndex;
    INSTRUCTION instr = code.getInstruction(sourceMachinecodeIndex);
    if ((delta>>>13) == 0) { // delta (positive) fits in 14 bits
      instr |= (delta<<2);
      code.putInstruction(sourceMachinecodeIndex, instr);
    } else {
      if (VM.VerifyAssertions) VM.assert((delta>>>23) == 0); // delta (positive) fits in 24 bits
      instr ^= 0x01000008; // make skip instruction with opposite sense
      code.putInstruction(sourceMachinecodeIndex-1, instr); // skip branch
      code.putInstruction(sourceMachinecodeIndex,   VM_Assembler.B(delta)); 
    }
  }

}
