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

import org.jikesrvm.compilers.opt.depgraph.DepGraphNode;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * A special dependence graph node for use by NormalBURS.<p>
 *
 * It provides some fields that are not used by any other
 * client of DepGraphNode.
 */
class NormalBURS_DepGraphNode extends DepGraphNode {

  private int predecessorCount;
  private AbstractBURS_TreeNode currentParent;

  NormalBURS_DepGraphNode(Instruction instr) {
    super(instr);
  }

  void setPredecessorCount(int newCount) {
    predecessorCount = newCount;
  }

  int getPredecessorCount() {
    return predecessorCount;
  }

  void incPredecessorCount() {
    predecessorCount++;
  }

  void decPredecessorCount() {
    predecessorCount--;
  }

  void setCurrentParent(AbstractBURS_TreeNode treeNode) {
    this.currentParent = treeNode;
  }

  AbstractBURS_TreeNode getCurrentParent() {
    return currentParent;
  }

}
