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

import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphEdge;

/**
 * Dependence graph edges: connect operands of different instructions
 * represented by dependence graph nodes.
 */
public final class DepGraphEdge extends SpaceEffGraphEdge implements DepGraphConstants {
  /**
   * Does this edge represent a register true dependence?
   * @return true if yes, false otherwise
   */
  public boolean isRegTrue() { return (scratch & REG_TRUE) != 0; }

  /**
   * Does this edge represent a register anti-dependence?
   * @return true if yes, false otherwise
   */
  public boolean isRegAnti() { return (scratch & REG_ANTI) != 0; }

  /**
   * Does this edge represent a register output dependence?
   * @return true if yes, false otherwise
   */
  public boolean isRegOutput() { return (scratch & REG_OUTPUT) != 0; }

  /**
   * Does this edge represent a register may def?
   * @return true if yes, false otherwise
   */
  public boolean isRegMayDef() { return (scratch & REG_MAY_DEF) != 0; }

  /**
   * Does this edge represent a memory true dependence?
   * @return true if yes, false otherwise
   */
  public boolean isMemTrue() { return (scratch & MEM_TRUE) != 0; }

  /**
   * Does this edge represent a memory anti-dependence?
   * @return true if yes, false otherwise
   */
  public boolean isMemAnti() { return (scratch & MEM_ANTI) != 0; }

  /**
   * Does this edge represent a memory output dependence?
   * @return true if yes, false otherwise
   */
  public boolean isMemOutput() { return (scratch & MEM_OUTPUT) != 0; }

  /**
   * Does this edge represent a memory reads-kill dependence?
   * @return true if yes, false otherwise
   */
  public boolean isMemReadsKill() { return (scratch & MEM_READS_KILL) != 0; }

  /**
   * Does this edge represent a control dependence?
   * @return true if yes, false otherwise
   */
  public boolean isControl() { return (scratch & CONTROL) != 0; }

  /**
   * Does this edge represent an exception-exception dependence?
   * @return true if yes, false otherwise
   */
  public boolean isExceptionE() { return (scratch & EXCEPTION_E) != 0; }

  /**
   * Does this edge represent an exception-store dependence?
   * @return true if yes, false otherwise
   */
  public boolean isExceptionMS() { return (scratch & EXCEPTION_MS) != 0; }

  /**
   * Does this edge represent an exception-load dependence?
   * @return true if yes, false otherwise
   */
  public boolean isExceptionML() { return (scratch & EXCEPTION_ML) != 0; }

  /**
   * Does this edge represent an exception-register live dependence?
   * @return true if yes, false otherwise
   */
  public boolean isExceptionR() { return (scratch & EXCEPTION_R) != 0; }

  /**
   * Does this edge represent a guard true dependence?
   * @return true if yes, false otherwise
   */
  public boolean isGuardTrue() { return (scratch & GUARD_TRUE) != 0; }

  /**
   * Does this edge represent a guard anti-dependence?
   * @return true if yes, false otherwise
   */
  public boolean isGuardAnti() { return (scratch & GUARD_ANTI) != 0; }

  /**
   * Does this edge represent a guard output dependence?
   * @return true if yes, false otherwise
   */
  public boolean isGuardOutput() { return (scratch & GUARD_OUTPUT) != 0; }

  /**
   * Does a given edge represent a register true dependence?
   * Use to avoid a cast from SpaceEffGraphEdge to DepGraphEdge.
   * @param edge the edge to test
   * @return true if yes, false otherwise
   */
  public static boolean isRegTrue(SpaceEffGraphEdge edge) {
    return (edge.getInfo() & REG_TRUE) != 0;
  }

  /**
   * Does a given edge represent a register anti-dependence?
   * Use to avoid a cast from SpaceEffGraphEdge to DepGraphEdge.
   * @param edge the edge to test
   * @return true if yes, false otherwise
   */
  public static boolean isRegAnti(SpaceEffGraphEdge edge) {
    return (edge.getInfo() & REG_ANTI) != 0;
  }

  /**
   * Does a given edge represent a register output dependence?
   * Use to avoid a cast from SpaceEffGraphEdge to DepGraphEdge.
   * @param edge the edge to test
   * @return true if yes, false otherwise
   */
  public static boolean isRegOutput(SpaceEffGraphEdge edge) {
    return (edge.getInfo() & REG_OUTPUT) != 0;
  }

  /**
   * The destination operand (of a REG_TRUE dependence)
   */
  private RegisterOperand _destOperand;

  /**
   * Augment the type of the dependence edge.
   * @param type the additional type for the edge
   */
  void addDepType(int type) { scratch |= type; }

  /**
   * @param sourceNode source dependence graph node
   * @param destNode destination dependence graph node
   * @param depKind the type of the dependence edge
   */
  DepGraphEdge(DepGraphNode sourceNode, DepGraphNode destNode, int depKind) {
    this(null, sourceNode, destNode, depKind);
  }

  /**
   * Constructor for dependence graph edge of a REG_TRUE dependence
   * from sourceNode to destNode due to destOp
   * @param destOp destination operand
   * @param sourceNode source dependence graph node
   * @param destNode destination dependence graph node
   * @param depKind the type of the dependence edge
   */
  DepGraphEdge(RegisterOperand destOp, DepGraphNode sourceNode, DepGraphNode destNode, int depKind) {
    super(sourceNode, destNode);
    _destOperand = destOp;
    setInfo(depKind);
  }

  /**
   * Get the type of the dependence edge.
   * @return type of the dependence edge
   */
  int depKind() {
    return getInfo();
  }

  /**
   * Get the destination operand.
   * @return destination operand
   */
  RegisterOperand destOperand() {
    return _destOperand;
  }

  /**
   * Get the string representation of edge type (used for printing).
   * @return string representation of edge type
   */
  @Override
  public String getTypeString() {
    String result = "";
    if (isRegTrue()) {
      result += " REG_TRUE ";
    }
    if (isRegAnti()) {
      result += " REG_ANTI ";
    }
    if (isRegOutput()) {
      result += " REG_OUT  ";
    }
    if (isMemTrue()) {
      result += " MEM_TRUE ";
    }
    if (isMemAnti()) {
      result += " MEM_ANTI ";
    }
    if (isMemOutput()) {
      result += " MEM_OUT  ";
    }
    if (isMemReadsKill()) {
      result += " MEM_READS_KILL  ";
    }
    if (isControl()) {
      result += " CONTROL  ";
    }
    if (isExceptionE()) {
      result += " EXCEP_E  ";
    }
    if (isExceptionMS()) {
      result += " EXCEP_MS ";
    }
    if (isExceptionML()) {
      result += " EXCEP_ML ";
    }
    if (isExceptionR()) {
      result += " EXCEP_R  ";
    }
    if (isGuardTrue()) {
      result += " GUARD_TRUE ";
    }
    if (isGuardAnti()) {
      result += " GUARD_ANTI ";
    }
    if (isGuardOutput()) {
      result += " GUARD_OUT  ";
    }
    if (isRegMayDef()) {
      result += " REG_MAY_DEF";
    }
    return result;
  }

  /**
   * Returns the string representation of the edge.
   * @return string representation of the edge
   */
  @Override
  public String toString() {
    return _fromNode + " ---> " + _toNode + getTypeString();
  }

  /**
   * Returns the string representation of the end node (used for printing).
   * @return string representation of the end node
   * @see SpaceEffGraphEdge#toNodeString()
   */
  @Override
  public String toNodeString() {
    return getTypeString() + " " + _toNode;
  }

  /**
   * Returns the string representation of the start node (used for printing).
   * @return string representation of the start node
   * @see SpaceEffGraphEdge#fromNodeString()
   */
  @Override
  public String fromNodeString() {
    return getTypeString() + " " + _fromNode;
  }

  /**
   * Return the input edge for a given node that corresponds to a given operand.
   * @param n destination node
   * @param op destination operand
   * @return input edge or null if not found
   */
  public static DepGraphEdge findInputEdge(DepGraphNode n, Operand op) {
    for (DepGraphEdge inEdge = (DepGraphEdge) n.firstInEdge(); inEdge != null; inEdge =
        (DepGraphEdge) inEdge.getNextIn()) {
      if (inEdge.destOperand() == op) {
        return inEdge;
      }
    }
    return null; // edge not found
  }
}



