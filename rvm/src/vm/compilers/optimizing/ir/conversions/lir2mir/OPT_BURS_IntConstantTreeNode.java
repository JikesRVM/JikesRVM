/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;

/**
 * A subclass of OPT_BURS_TreeNode for an IntConstantOperand.
 * It is very common for us to want to access the value of an 
 * int constant during BURS, so we make it easy to do so by creating
 * a special kind of node.
 * 
 * @author David Grove
 */
final class OPT_BURS_IntConstantTreeNode extends OPT_BURS_TreeNode {

  final int value;

  /**
   * Constructor for interior node.
   */
  OPT_BURS_IntConstantTreeNode(int val) {
    super(OPT_Operators.INT_CONSTANT_opcode);
    value = val;
    setNumRegisters(0);
  }
 
  public String toString() {
    return "INT_CONSTANT "+value;
  }
}
