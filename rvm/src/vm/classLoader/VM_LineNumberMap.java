/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM_Uninterruptible;
import java.io.DataInputStream;
import java.io.IOException;

/**
 *  A java method's source line number information.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_LineNumberMap implements VM_Uninterruptible {
   
  // Note that line mappings for a method appear in order of increasing bytecode offset.
  // The same line number can appear more than once (each with a different bytecode offset).

  /**
   * bytecode offset at which each instruction sequence begins
   * 0-indexed from start of method's bytecodes[]     
   */
  private final short[] startPCs;    

  /** 
   * line number at which each instruction sequence begins
   * 1-indexed from start of method's source file
   */
  private final short[] lineNumbers;

  VM_LineNumberMap(DataInputStream input, int n) throws IOException {
    startPCs    = new short[n];
    lineNumbers = new short[n];
    for (int i = 0; i<n; i++) {
      startPCs[i]    = input.readShort();
      lineNumbers[i] = input.readShort();
    }
  }

  /**
   * Return the line number information for the argument bytecode index.
   */
  public final int getLineNumberForBCIndex(int bci) {
    int idx;
    for (idx = 0; idx < startPCs.length; idx++) {
      int pc = 0xffff & (int)startPCs[idx];
      if (bci < pc) {
	if (idx == 0) idx++; // add 1, so we can subtract 1 below.
	break;
      }
    }
    return 0xffff & (int)lineNumbers[--idx];
  }
}
