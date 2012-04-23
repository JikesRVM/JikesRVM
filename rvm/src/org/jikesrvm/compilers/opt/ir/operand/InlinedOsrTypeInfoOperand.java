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
import org.jikesrvm.classloader.MemberReference;
/*
 * An InlinedOsrTypeInfoOperand object keeps necessary information
 * to recover non-inlined status for an inlined method.
 */

public final class InlinedOsrTypeInfoOperand extends Operand {

  ////////////////////////////////////////////
  //             DATA Type                  //
  ////////////////////////////////////////////

  /* the type info is organized by calling sequences, e.g.,
   * a calls b calls c, then the type information is
   * methodids: a_ids, b_ids, c_ids
   * bcindexes: a_pc,  b_pc,  c_pc
   * localsize: a_lsize, b_lsize, c_lsize
   * stacksize: a_ssize, b_sszie, c_sszie
   * localTypeCodes |-- a_lsize --|-- b_lsize --|-- c_lsize --|
   * stackTypeCodes |-- a_ssize --|-- b_ssize --|-- c_ssize --|
   */
  public int[] methodids;
  public int[] bcindexes;
  public byte[][] localTypeCodes;
  public byte[][] stackTypeCodes;

  public int validOps;

  /* operands of OsrPoint is laid out as following:
     | locals 1 | stacks 1 | locals 2 | stacks 2 | ....
   */

  public InlinedOsrTypeInfoOperand(int[] mids, int[] cpcs, byte[][] ltypes, byte[][] stypes) {
    this.methodids = mids;
    this.bcindexes = cpcs;
    this.localTypeCodes = ltypes;
    this.stackTypeCodes = stypes;
  }

  /**
   * Return a new operand that is semantically equivalent to <code>this</code>.
   *
   * @return a copy of <code>this</code>
   */
  @Override
  public Operand copy() {
    return new InlinedOsrTypeInfoOperand(methodids, bcindexes, localTypeCodes, stackTypeCodes);
  }

  /**
   * Are two operands semantically equivalent?
   *
   * @param op other operand
   * @return   <code>true</code> if <code>this</code> and <code>op</code>
   *           are semantically equivalent or <code>false</code>
   *           if they are not.
   */
  @Override
  public boolean similar(Operand op) {
    boolean result = true;

    if (!(op instanceof InlinedOsrTypeInfoOperand)) {
      return false;
    }

    InlinedOsrTypeInfoOperand other = (InlinedOsrTypeInfoOperand) op;

    result =
        Arrays.equals(this.methodids, other.methodids) &&
        Arrays.equals(this.bcindexes, other.bcindexes) &&
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
    StringBuffer buf = new StringBuffer("(");

    for (int i = 0, n = methodids.length; i < n; i++) {
      buf.append(bcindexes[i]).append("@").append(MemberReference.getMemberRef(methodids[i]).getName()).append(" : ");

      for (int j = 0, m = localTypeCodes[i].length; j < m; j++) {
        buf.append((char) localTypeCodes[i][j]);
      }

      buf.append(",");
      for (int j = 0, m = stackTypeCodes[i].length; j < m; j++) {
        buf.append((char) stackTypeCodes[i][j]);
      }

      if (i != n - 1) {
        buf.append(" | ");
      }
    }
    buf.append(")");
    return new String(buf);
  }
}
