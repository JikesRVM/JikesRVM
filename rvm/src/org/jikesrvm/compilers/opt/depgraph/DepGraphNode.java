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
package org.jikesrvm.compilers.opt.depgraph;

import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;

/**
 * Dependence graph node: there is one for each instruction in a basic block.
 */
public final class DepGraphNode extends SpaceEffGraphNode implements DepGraphConstants {

  /**
   * Instruction that this node represents.
   */
  public final Instruction _instr;

  /**
   * Constructor.
   * @param instr the instruction this node represents
   */
  public DepGraphNode(Instruction instr) {
    _instr = instr;
  }

  /**
   * Get the instruction this node represents.
   * @return instruction this node represents
   */
  public Instruction instruction() {
    return _instr;
  }

  /**
   * Returns the string representation of this node.
   * @return string representation of this node
   */
  @Override
  public String toString() {
    return "[" + _instr + "]";
  }

  /**
   * Add an out edge from this node to the given node.
   * @param node destination node for the edge
   * @param type the type of the edge to add
   */
  public void insertOutEdge(DepGraphNode node, int type) {
    if (COMPACT) {
      int numTries = 0; // bound to avoid quadratic blowup.
      for (DepGraphEdge oe = (DepGraphEdge) firstOutEdge(); oe != null && numTries < 4; oe =
          (DepGraphEdge) oe.getNextOut(), numTries++) {
        if (oe.toNode() == node) {
          oe.addDepType(type);
          return;
        }
      }
    }
    DepGraphEdge edge = new DepGraphEdge(this, node, type);
    this.appendOutEdge(edge);
    node.appendInEdge(edge);
  }

  /**
   * Add an out edge this node to the given node
   * because of a register true dependence of a given operand.
   * @param node destination node for the edge
   * @param op   the operand of node that is defined by this edge
   */
  public void insertRegTrueOutEdge(DepGraphNode node, RegisterOperand op) {
    DepGraphEdge e = new DepGraphEdge(op, this, node, REG_TRUE);
    this.appendOutEdge(e);
    node.appendInEdge(e);
  }
}
