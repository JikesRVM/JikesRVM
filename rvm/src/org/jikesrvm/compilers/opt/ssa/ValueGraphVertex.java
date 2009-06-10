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
package org.jikesrvm.compilers.opt.ssa;

import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;

/**
 * This class implements a vertex in the value graph used in global
 * value numbering
 * ala Alpern, Wegman and Zadeck.  See Muchnick p.348 for a nice
 * discussion.
 */
final class ValueGraphVertex extends SpaceEffGraphNode {
  /** the name of the variable defined by this node */
  private final Object name;
  /** the name of the operator that does the definition */
  private Object label;
  /** operand vertices, in order */
  private ValueGraphVertex[] targets;
  /** integer value number */
  private int valueNumber;
  /** number of operands needed */
  private int arity;

  ValueGraphVertex(Object name) {
    this.name = name;
  }

  /**
   * Set up properties of this vertex identically to another vertex
   */
  void copyVertex(ValueGraphVertex v) {
    this.label = v.label;
    this.valueNumber = v.valueNumber;
    this.arity = v.arity;
    this.targets = new ValueGraphVertex[v.targets.length];
    for (int i = 0; i < targets.length; i++) {
      this.targets[i] = v.targets[i];
    }
  }

  /**
   * Does this vertex represent an incoming parameter?
   */
  boolean representsParameter() {
    return (label instanceof ValueGraphParamLabel);
  }

  /**
   * Set the label for this vertex.
   *
   * @param label the label (an operator of some type)
   * @param arity the number of operands needed
   */
  void setLabel(Object label, int arity) {
    this.label = label;
    this.arity = arity;
    targets = new ValueGraphVertex[arity];
  }

  Object getLabel() {
    return label;
  }

  Object getName() {
    return name;
  }

  int getValueNumber() {
    return valueNumber;
  }

  void setValueNumber(int number) {
    valueNumber = number;
  }

  boolean isConstant() {
    return (label instanceof ConstantOperand);
  }

  // is the def for this node an allocation instruction?
  boolean isBornAtAllocation() {
    return (label instanceof Instruction);
  }

  /**
   * return the target of the ith operand of this node
   */
  public ValueGraphVertex getTarget(int i) {
    return targets[i];
  }

  public void addTarget(ValueGraphVertex target, int pos) {
    targets[pos] = target;
  }

  public int getArity() {
    return arity;
  }

  public String toString() {
    StringBuilder s = new StringBuilder("Vertex: " + name + " " + label);
    s.append(" Targets: ");
    for (int i = 0; i < arity; i++) {
      if (targets[i] == null) {
        s.append("null  ");
      } else {
        s.append(targets[i].getName()).append("  ");
      }
    }
    return s.toString();
  }
}



