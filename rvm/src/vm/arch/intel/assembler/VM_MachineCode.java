/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;
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
