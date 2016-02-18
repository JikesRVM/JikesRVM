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

import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.util.BitVector;

/**
 * This structure holds dominator-related information for a basic block.
 */
public class DominatorInfo {
  /**
   * A BitVector which represents the dominators of the basic block
   */
  final BitVector dominators;
  /**
   * The basic block's immediate dominator.
   */
  BasicBlock idom;

  /**
   * Make a structure with a given bit set holding the dominators
   * of the basic block.
   *
   * @param  dominators the bit set
   */
  DominatorInfo(BitVector dominators) {
    this.dominators = dominators;
  }

  /**
   * Is the basic block represented by this structure dominated by another
   * basic block?
   *
   * @param bb the basic block in question
   * @return true or false
   */
  public boolean isDominatedBy(BasicBlock bb) {
    return dominators.get(bb.getNumber());
  }

}
