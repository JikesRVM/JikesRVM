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

import org.jikesrvm.*;
import org.jikesrvm.compilers.opt.depgraph.DepGraphNode;
import org.jikesrvm.compilers.opt.ir.*;

/**
 * An AbstractBURS_TreeNode is a node in a binary tree that is fed
 * as input to BURS. Machine generated versions are created for
 * every architecture and address size.
 *
 * @see BURS
 * @see BURS_StateCoder
 */
public abstract class AbstractBURS_TreeNode {

  AbstractBURS_TreeNode child1;
  AbstractBURS_TreeNode child2;

  /**
   * Dependence graph node corresponding to
   * interior node in BURS tree (set to null for
   * leaf node or for OTHER_OPERAND node).
   */
  public final DepGraphNode dg_node;

  /**
   * Opcode of instruction
   */
  private final char opcode;

  /**
   * nonterminal &gt; 0 ==&gt; this tree node is the
   * root of a "supernode"; the value of nonterminal
   * identifies the matching non-terminal
   * nonterminal = 0 ==&gt; this tree node is NOT the
   * root of a "supernode".
   */
  private byte nonterminal;

  /**
   * <pre>
   * trrr rrrr
   * t = tree root
   * r = num of registers used
   * </pre>
   */
  private byte treeroot_registersused;

  public final char getOpcode() {
    return opcode;
  }

  public final AbstractBURS_TreeNode getChild1() {
    return child1;
  }

  public final void setChild1(AbstractBURS_TreeNode x) {
    child1 = x;
  }

  public final AbstractBURS_TreeNode getChild2() {
    return child2;
  }

  public final void setChild2(AbstractBURS_TreeNode x) {
    child2 = x;
  }

  public final int getNonTerminal() {
     return nonterminal & 0xFF;
  }

  public final void setNonTerminal(int nonterminal) {
     if (VM.VerifyAssertions) VM._assert(nonterminal <= 0xff);
     this.nonterminal = (byte)nonterminal;
  }

  public final boolean isTreeRoot() {
     return (treeroot_registersused & 0x80) != 0;
  }

  public final void setTreeRoot() {
     treeroot_registersused |= 0x80;
  }

  public final void setNumRegisters(int r) {
    treeroot_registersused = (byte)((treeroot_registersused & 0x80) | (r & 0x7f));
  }
  public final int numRegisters() {
    return treeroot_registersused & 0x7f;
  }

  public final Instruction getInstruction() {
     return dg_node._instr;
  }

  public final String getInstructionString() {
    if (dg_node != null) {
      return dg_node._instr.toString();
    } else {
      return "";
    }
  }

  /**
   * Constructor for interior node.
   * @param n the dep graph node
   */
  protected AbstractBURS_TreeNode(DepGraphNode n) {
    Instruction instr = n._instr;
    dg_node = n;
    opcode = instr.getOpcode();
  }


  /**
   * @param n the dep graph node
   * @return an interior node
   */
  public static AbstractBURS_TreeNode create(DepGraphNode n) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        return new org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_TreeNode(n);
      } else {
        return new org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_TreeNode(n);
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        return new org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_TreeNode(n);
      } else {
        return new org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_TreeNode(n);
      }
    }
  }

  /**
   * Constructor for leaf/auxiliary node.
   * @param Opcode the opcode for the node
   */
  protected AbstractBURS_TreeNode(char Opcode) {
    dg_node = null;
    opcode = Opcode;
  }

  /**
   * @param Opcode the node's opcode
   * @return a leaf/auxiliary node.
   */
  public static AbstractBURS_TreeNode create(char Opcode) {
    if (VM.BuildForIA32) {
      if (VM.BuildFor32Addr) {
        return new org.jikesrvm.compilers.opt.lir2mir.ia32_32.BURS_TreeNode(Opcode);
      } else {
        return new org.jikesrvm.compilers.opt.lir2mir.ia32_64.BURS_TreeNode(Opcode);
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      if (VM.BuildFor32Addr) {
        return new org.jikesrvm.compilers.opt.lir2mir.ppc_32.BURS_TreeNode(Opcode);
      } else {
        return new org.jikesrvm.compilers.opt.lir2mir.ppc_64.BURS_TreeNode(Opcode);
      }
    }
  }

  @Override
  public String toString() {
    return Operator.lookupOpcode(getOpcode()).toString();
  }

  public final boolean isSuperNodeRoot() {
    return (getNonTerminal() > 0);
  }

  public final boolean isREGISTERNode() {
    return getOpcode() == Operators.REGISTER_opcode;
  }

 /**
  * Get the BURS rule number associated with this tree node for a given non-terminal
  *
  * @param goalNT the non-terminal we want to know the rule for (e.g. stm_NT)
  * @return the rule number
  */
  public abstract int rule(int goalNT);

  public abstract char getCost(int goalNT);

  public abstract void setCost(int goalNT, char cost);

  public abstract void initCost();

  public abstract void writePacked(int word, int mask, int shiftedValue);

  public abstract int readPacked(int word, int shift, int mask);
}
