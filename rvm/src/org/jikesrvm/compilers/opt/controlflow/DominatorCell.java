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
package org.jikesrvm.compilers.opt.controlflow;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.dfsolver.DF_AbstractCell;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.util.BitVector;

/**
 * DominatorCell represents a set of basic blocks, used in
 * the dataflow calculation
 */
class DominatorCell extends DF_AbstractCell {

  /**
   * Pointer to the governing IR.
   */
  final IR ir;
  /**
   * The basic block corresponding to this lattice cell.
   */
  final BasicBlock block;
  /**
   * Bit set representation of the dominators for this basic block.
   */
  BitVector dominators;
  /**
   * A guess of the upper bound on the number of out edges for most basic
   * blocks.
   */
  private static final int CAPACITY = 5;

  /**
   * Make a bit set for a basic block
   * @param bb the basic block
   * @param ir the governing IR
   */
  public DominatorCell(BasicBlock bb, IR ir) {
    super(CAPACITY);
    block = bb;
    dominators = new BitVector(ir.getMaxBasicBlockNumber() + 1);
    this.ir = ir;
  }

  /**
   * Return a String representation of this cell.
   * @return a String representation of this cell.
   */
  @Override
  public String toString() {
    return block + ":" + dominators;
  }

  /**
   * Include a single basic block in this set.
   * @param bb the basic block
   */
  public void addSingleBlock(BasicBlock bb) {
    dominators.set(bb.getNumber());
  }

  /**
   * Include all basic blocks in this set.
   * <p> TODO: make this more efficient.
   * @param ir the governing ir
   */
  public void setTOP(IR ir) {
    for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
      BasicBlock b = e.nextElement();
      dominators.set(b.getNumber());
    }
  }
}
