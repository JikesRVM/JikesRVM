/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.ia32;

import org.jikesrvm.ArchitectureSpecific;

/**
 */
public abstract class MachineCode {
  private final ArchitectureSpecific.CodeArray instructions;

  // TODO: This should really be a final field, but is not due to the way
  //       OSR is currently implemented.
  private int[] bytecodeMap;

  public MachineCode(ArchitectureSpecific.CodeArray i, int[] bm) {
    instructions = i;
    bytecodeMap = bm;
  }

  public final ArchitectureSpecific.CodeArray getInstructions() {
    return instructions;
  }

  public final int[] getBytecodeMap() {
    return bytecodeMap;
  }

  public void setBytecodeMap(int[] b2m) {
    bytecodeMap = b2m;
  }
}
