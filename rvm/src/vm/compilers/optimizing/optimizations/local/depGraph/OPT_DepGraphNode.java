/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;

/**
 * Dependence graph node: there is one for each instruction in a basic block.
 * @author Harini Srinivasan
 * @author Igor Pechtchanski
 */
public final class OPT_DepGraphNode extends OPT_SpaceEffGraphNode 
  implements OPT_DepGraphConstants {

  /**
   * Instruction that this node represents.
   */
  OPT_Instruction _instr;

  /**
   * Constructor.
   * @param instr the instruction this node represents
   */
  OPT_DepGraphNode(OPT_Instruction instr) {
    _instr = instr;
  }

  /**
   * Get the instruction this node represents.
   * @return instruction this node represents
   */
  OPT_Instruction instruction() {
    return  _instr;
  }

  /**
   * Returns the string representation of this node.
   * @return string representation of this node
   */
  public String toString() {
    return  "[" + _instr + "]";
  }

  /**
   * Returns a VCG descriptor for the node which will provide VCG-relevant
   * information for the node.
   * @return node descriptor
   * @see OPT_VCGNode#getVCGDescriptor
   */
  public OPT_VCGNode.NodeDesc getVCGDescriptor() {
    return new OPT_VCGNode.NodeDesc() {
      public String getLabel() { return _instr.toString(); }
    };
  }

  /**
   * Add an out edge from this node to the given node.
   * @param node destination node for the edge
   * @param type the type of the edge to add
   * @author Mauricio J. Serrano
   */
  public void insertOutEdge(OPT_DepGraphNode node, int type) {
    if (COMPACT) {
      int numTries = 0; // bound to avoid quadratic blowup.
      for (OPT_DepGraphEdge oe = (OPT_DepGraphEdge) firstOutEdge(); 
           oe != null && numTries < 4; 
           oe = (OPT_DepGraphEdge) oe.getNextOut(), numTries++) {
        if (oe.toNode() == node) {
          oe.addDepType(type);
          return;
        }
      }
    }
    OPT_DepGraphEdge edge = new OPT_DepGraphEdge(this, node, type);
    this.appendOutEdge(edge);
    node.appendInEdge(edge);
  }

  /**
   * Add an out edge this node to the given node
   * because of a register true dependence of a given operand.
   * @param node destination node for the edge
   * @param op   the operand of node that is defined by this edge
   */
  public void insertRegTrueOutEdge(OPT_DepGraphNode node, 
                                   OPT_RegisterOperand op) {
    OPT_DepGraphEdge e = new OPT_DepGraphEdge(op, this, node, REG_TRUE);
    this.appendOutEdge(e);
    node.appendInEdge(e);
  }
}

