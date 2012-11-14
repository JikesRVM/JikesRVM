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
package org.jikesrvm.compilers.opt.bc2ir;

import org.jikesrvm.compilers.opt.ir.operand.Operand;

/**
 * ReturnAddress operand. Used to represent the address pushed on
 * the expression stack by a JSR instruction.
 */
public final class ReturnAddressOperand extends Operand {
  final int retIndex;

  ReturnAddressOperand(int ri) { retIndex = ri; }

  @Override
  public Operand copy() { return this; }

  @Override
  public boolean similar(Operand op) {
    return (op instanceof ReturnAddressOperand) && (retIndex == ((ReturnAddressOperand) op).retIndex);
  }

  @Override
  public String toString() {
    return "<return address " + retIndex + ">";
  }
}
