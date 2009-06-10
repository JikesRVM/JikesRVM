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
package org.jikesrvm.compilers.opt.lir2mir;

import org.jikesrvm.ArchitectureSpecificOpt.BURS_TreeNode;
import org.jikesrvm.compilers.opt.ir.Operators;

/**
 * A subclass of BURS_TreeNode for an IntConstantOperand.
 * It is very common for us to want to access the value of an
 * int constant during BURS, so we make it easy to do so by creating
 * a special kind of node.
 */
final class BURS_IntConstantTreeNode extends BURS_TreeNode {

  final int value;

  /**
   * Constructor for interior node.
   */
  BURS_IntConstantTreeNode(int val) {
    super(Operators.INT_CONSTANT_opcode);
    value = val;
    setNumRegisters(0);
  }

  public String toString() {
    return "INT_CONSTANT " + value;
  }
}
