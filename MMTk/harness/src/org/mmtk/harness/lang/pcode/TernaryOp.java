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
package org.mmtk.harness.lang.pcode;

import org.mmtk.harness.lang.compiler.Register;

public abstract class TernaryOp extends PseudoOp {

  protected int op1;
  protected int op2;
  protected int op3;

  public TernaryOp(String name, Register resultTemp, Register op1, Register op2, Register op3) {
    super(3, name, resultTemp);
    this.op1 = op1.getIndex();
    this.op2 = op2.getIndex();
    this.op3 = op3.getIndex();
  }

  public TernaryOp(String name, Register op1, Register op2, Register op3) {
    super(3, name);
    this.op1 = op1.getIndex();
    this.op2 = op2.getIndex();
    this.op3 = op3.getIndex();
  }

  public String toString() {
    return String.format("%s(%s,%s,%s)", super.toString(),
        Register.nameOf(op1), Register.nameOf(op2), Register.nameOf(op3));
  }
}
