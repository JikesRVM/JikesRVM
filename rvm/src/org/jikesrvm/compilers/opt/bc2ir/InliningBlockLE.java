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
package org.jikesrvm.compilers.opt.bc2ir;

import org.jikesrvm.compilers.opt.ir.BasicBlock;

/**
 * Extend BasicBlockLE to support inlining during IR generation.
 */
final class InliningBlockLE extends BasicBlockLE {
  final GenerationContext gc;
  final BasicBlockLE epilogueBBLE;

  InliningBlockLE(GenerationContext c, BasicBlockLE bble) {
    super(0);
    gc = c;
    epilogueBBLE = bble;
  }

  @Override
  public String toString() {
    return "(Inline method " + gc.method + ")";
  }

  /**
   * delete the outgoing CFG edges from all
   * basic blocks in the callee (gc.cfg).
   * This is used when the BBLE preceeding the inlined
   * method block needs to be regenerated, thus forcing
   * us to discard the callee IR (which may contains
   * control flow links to the caller IR because of exception handlers).
   * <p>
   * TODO: One might be able to do this more efficiently by
   * keeping track of the exposed edges in the generation context
   * and commiting them once the top level generation
   * completes.  Probably not worth it, since we expect this
   * method to be called very infrequently.
   */
  void deleteAllOutEdges() {
    for (BasicBlock bb = gc.cfg.firstInCodeOrder(); bb != null; bb = bb.nextBasicBlockInCodeOrder()) {
      bb.deleteOut();
    }
  }
}
