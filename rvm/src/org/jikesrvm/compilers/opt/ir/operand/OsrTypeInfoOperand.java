/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.ir.operand;

import java.util.Arrays;
/**
 * An OsrTypeInfoOperand object keeps type information of locals
 * and stacks at a byte code index.
 */
public final class OsrTypeInfoOperand extends Operand {

  /**
   * The data type.
   */
  public byte[] localTypeCodes;
  public byte[] stackTypeCodes;

  /**
   * Create a new type operand with the specified data type.
   */
  public OsrTypeInfoOperand(byte[] ltcodes, byte[] stcodes) {
    this.localTypeCodes = ltcodes;
    this.stackTypeCodes = stcodes;
  }

  @Override
  public Operand copy() {
    return new OsrTypeInfoOperand(localTypeCodes, stackTypeCodes);
  }

  @Override
  public boolean similar(Operand op) {
    boolean result = true;

    if (!(op instanceof OsrTypeInfoOperand)) {
      return false;
    }

    OsrTypeInfoOperand other = (OsrTypeInfoOperand) op;

    result =
        Arrays.equals(this.localTypeCodes, other.localTypeCodes) &&
        Arrays.equals(this.stackTypeCodes, other.stackTypeCodes);

    return result;
  }

  /**
   * Returns the string representation of this operand.
   *
   * @return a string representation of this operand.
   */
  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder("OsrTypeInfo(");
    for (int i = 0, n = localTypeCodes.length; i < n; i++) {
      buf.append((char) localTypeCodes[i]);
    }

    buf.append(",");
    for (int i = 0, n = stackTypeCodes.length; i < n; i++) {
      buf.append((char) stackTypeCodes[i]);
    }

    buf.append(")");

    return buf.toString();
  }
}
