/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import java.io.DataInputStream;
import java.io.IOException;

/**
 *  A java method's source line number information.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_LineNumberMap implements VM_Uninterruptible {
   
  // Note that line mappings for a method appear in order of increasing bytecode offset.
  // The same line number can appear more than once (each with a different bytecode offset).

  /**
   * bytecode offset at which each instruction sequence begins
   * 0-indexed from start of method's bytecodes[]     
   */
  int[] startPCs;    

  /** 
   * line number at which each instruction sequence begins
   * 1-indexed from start of method's source file
   */
  int[] lineNumbers;
   
  VM_LineNumberMap(int n) {
    startPCs    = new int[n];
    lineNumbers = new int[n];
  }

  VM_LineNumberMap(DataInputStream input, int n) throws IOException {
    this(n);
    for (int i = 0; i < n; ++i) {
      startPCs[i]    = input.readUnsignedShort();
      lineNumbers[i] = input.readUnsignedShort();
    }
  }

  /**
   * Return the line number information for the argument bytecode index.
   */
  final int getLineNumberForBCIndex(int bci) {
    int idx;
    for (idx = 0; idx < startPCs.length; idx++) {
      if (bci < startPCs[idx]) {
	if (idx == 0) idx++; // add 1, so we can subtract 1 below.
	break;
      }
    }
    return lineNumbers[--idx];
  }

}
