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

import org.jikesrvm.compilers.opt.depgraph.DepGraph;
import org.jikesrvm.compilers.opt.depgraph.DepGraphNode;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.Instruction;

/**
 * A special dependence graph for use by NormalBURS.<p>
 *
 * All nodes in this graph are NormalBURS_DepGraph nodes.
 */
public class NormalBURS_DepGraph extends DepGraph {

  public NormalBURS_DepGraph(IR ir, Instruction start, Instruction end, BasicBlock currentBlock) {
    super(ir, start, end, currentBlock);
  }

  @Override
  public DepGraphNode createDepGraphNode(Instruction inst) {
    return new NormalBURS_DepGraphNode(inst);
  }

}
