/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;

/**
 */
public abstract class VM_MachineCode {
  private final ArchitectureSpecific.VM_CodeArray instructions;

  // TODO: This should really be a final field, but is not due to the way
  //       OSR is currently implemented.
  private int[] bytecodeMap;

  public VM_MachineCode(ArchitectureSpecific.VM_CodeArray i, int[] bm) {
    instructions = i;
    bytecodeMap = bm;
  }

  public final ArchitectureSpecific.VM_CodeArray getInstructions() {
    return instructions;
  }

  public final int[] getBytecodeMap() {
    return bytecodeMap;
  }

  public void setBytecodeMap(int[] b2m) {
    bytecodeMap = b2m;
  }
}
