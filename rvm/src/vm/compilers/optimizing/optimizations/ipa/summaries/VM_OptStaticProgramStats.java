/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;
import com.ibm.JikesRVM.*;

/**
 * A class that encapsulates various static program characteristics.
 *
 * @author Stephen Fink
 */
public final class VM_OptStaticProgramStats {

  private static int nClasses;
  private static int nMethods;
  private static int nBytecodes;

  /**
   * Reset the information about the program.  
   */
  static void reset() {
    nClasses = nMethods = nBytecodes = 0;
  }

  /**
   * Call this method when a new method is compiled.
   * @param size the number of bytecodes in the new method
   */
  static void newMethod(int size) {
    nMethods++;
    nBytecodes += size;
  }
  /**
   * Call this method when a new class is loaded
   * @param size the number of bytecodes in the new method
   */
  static void newClass() {
    nClasses++;
  }

  /**
   * Print a report.
   */
  public static void report() {
    VM.sysWrite("Number of classes loaded: ");
    VM.sysWrite(nClasses);
    VM.sysWrite("\n");
    VM.sysWrite("Number of methods loaded: ");
    VM.sysWrite(nMethods);
    VM.sysWrite("\n");
    VM.sysWrite("Number of bytecodes compiled: ");
    VM.sysWrite(nBytecodes);
    VM.sysWrite("\n");
  } 
}
