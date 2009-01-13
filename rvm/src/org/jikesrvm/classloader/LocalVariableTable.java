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
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * A java method's local variable information
 */
public final class LocalVariableTable {
  /**
   * Local variables in this table
   */
  private final LocalVariable[] locals;

  /**
   * Information needed to describe a local variable
   */
  static class LocalVariable {
    /**
     * The start PC location where the variable is active
     */
    private final int startPC;

    /**
     * The variable is active in PC values [startPC, startPC+length].
     */
    private final int length;

    /**
     * The variable's name.
     */
    private final Atom name;

    /**
     * The variable's type descriptor.
     */
    private final Atom descriptor;

    /**
     * The slot on the local variable stack where the variable is stored.
     */
    private final int frameIndex;

    /**
     * Construct a local variable.
     *
     * @param startPC
     * @param length
     * @param name
     * @param descriptor
     * @param frameIndex
     */
    LocalVariable(int startPC, int length, Atom name, Atom descriptor, int frameIndex) {
      this.startPC = startPC;
      this.length = length;
      this.name = name;
      this.descriptor = descriptor;
      this.frameIndex = frameIndex;
    }

    /**
     * String represenation of this local variable.
     */
    public String toString() {
      return (startPC + " " +
             length + " " +
             name.toString() + " " +
             descriptor.toString() + " " +
             frameIndex + "\n");
    }
  }

  /**
   * Construct the local variable table
   *
   * @param locals
   */
  LocalVariableTable(LocalVariable[] locals) {
    this.locals = locals;
  }

  /**
   * Read the local variable table
   *
   * @return a local variable table or null if none were present
   */
  static LocalVariableTable readLocalVariableTable(DataInputStream input, int[] constantPool) throws IOException {
    int numVars = input.readUnsignedShort();
    if (numVars > 0) {
      LocalVariable[] lvs = new LocalVariable[numVars];
      for (int i = 0; i < numVars; ++i) {
        LocalVariable lv = new LocalVariable(
            input.readUnsignedShort(),
            input.readUnsignedShort(),
            ClassFileReader.getUtf(constantPool, input.readUnsignedShort()),
            ClassFileReader.getUtf(constantPool, input.readUnsignedShort()),
            input.readUnsignedShort());
        lvs[i] = lv;
      }
      return new LocalVariableTable(lvs);
    } else {
      return null;
    }
  }

  /**
   * String representation of the local variable table.
   */
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Local Variable Table: \n");
    for (LocalVariable lv : locals) {
      sb.append(lv.toString());
    }
    return sb.toString();
  }
}
