/*
 * (C) Copyright IBM Corp. 2001
 */

class VM_SwitchCase extends VM_ForwardReference {

  VM_SwitchCase (int source, int btarget) {
    super(source, btarget);
  }

  void resolve (VM_MachineCode code, int target) {
    if (VM.TraceAssembler) 
      System.out.print(" <+ " + VM_Assembler.hex(sourceMachinecodeIndex << 2));
    int delta = target - sourceMachinecodeIndex;
    // correction is number of words of source off switch base
    int         correction = (int)code.getInstruction(sourceMachinecodeIndex);
    INSTRUCTION offset = (INSTRUCTION)((delta+correction) << 2);
    code.putInstruction(sourceMachinecodeIndex, offset);
  }

}
