/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

/** 
 * @author Julian Dolby
 */
class VM_MachineCode {
  private VM_CodeArray instructions;
  private int[] bytecodeMap;

  VM_MachineCode (VM_CodeArray i, int[] bm) {
    instructions = i;
    bytecodeMap  = bm;
  }

  final VM_CodeArray getInstructions () {
    return instructions;
  }

  final int[] getBytecodeMap () {
    return bytecodeMap;
  }

  //-#if RVM_WITH_OSR
  void setBytecodeMap(int b2m[]) {
    bytecodeMap = b2m;
  }
  //-#endif
}
