/*
 * (C) Copyright IBM Corp. 2001
 */
class VM_MachineCode {

  /* interface */

  VM_MachineCode (INSTRUCTION[] i, int[] bm) {
    instructions = i;
    bytecodeMap  = bm;
  }

  final INSTRUCTION[] getInstructions () {
    return instructions;
  }

  final int[] getBytecodeMap () {
    return bytecodeMap;
  }

  /* implementation */

  private INSTRUCTION[] instructions;
  private int  [] bytecodeMap;

}
