/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
/** 
 * @author Julian Dolby
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
