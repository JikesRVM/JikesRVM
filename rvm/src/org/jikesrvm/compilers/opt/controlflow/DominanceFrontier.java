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

import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.util.TreeNode;
import org.jikesrvm.util.BitVector;

/**
 * Calculate dominance frontier for a set of basic blocks.
 *
 * <p> Uses the algorithm of Cytron et al., TOPLAS Oct. 91:
 *
 * <pre>
 * for each X in a bottom-up traversal of the dominator tree do
 *
 *      DF(X) < - null
 *      for each Y in Succ(X) do
 *        if (idom(Y)!=X) then DF(X) <- DF(X) U Y
 *      end
 *      for each Z in {idom(z) = X} do
 *        for each Y in DF(Z) do
 *              if (idom(Y)!=X) then DF(X) <- DF(X) U Y
 *        end
 *      end
 * </pre>
 *
 * <p> TODO: we do not support IRs with exception handlers!!
 */
public class DominanceFrontier extends CompilerPhase {
  /**
   * Should this phase be performed?  This is a member of a composite
   * phase, so always return true.  The parent composite phase will
   * dictate.
   * @param options controlling compiler options
   */
  @Override
  public final boolean shouldPerform(OptOptions options) {
    return true;
  }

  /**
   * Return this instance of this phase. This phase contains no
   * per-compilation instance fields.
   * @param ir not used
   * @return this
   */
  @Override
  public CompilerPhase newExecution(IR ir) {
    return this;
  }

  /**
   * Return a String representation for this phase
   * @return a String representation for this phase
   */
  @Override
  public final String getName() {
    return "Dominance Frontier";
  }

  /**
   * Should the IR be printed either before or after performing this phase?
   *
   * @param options controlling compiler options
   * @param before true iff querying before the phase
   * @return true or false
   */
  @Override
  public final boolean printingEnabled(OptOptions options, boolean before) {
    return false;
  }

  /**
   * Calculate the dominance frontier for each basic block in the
   * CFG. Stores the result in the DominatorTree for the governing
   * IR.
   *
   * <p> NOTE: The dominator tree MUST be calculated BEFORE calling this
   *      routine.
   *
   * @param ir the governing IR
   */
  @Override
  public void perform(IR ir) {
    final boolean DEBUG = false;
    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed) {
      return;
    }
    // make sure dominator tree is computed
    if (ir.HIRInfo.dominatorTree == null) {
      return;
    }
    // for each X in a bottom-up traversal of the dominator tree do
    DominatorTree tree = ir.HIRInfo.dominatorTree;
    for (Enumeration<TreeNode> x = tree.getBottomUpEnumerator(); x.hasMoreElements();) {
      DominatorTreeNode v = (DominatorTreeNode) x.nextElement();
      BasicBlock X = v.getBlock();
      if (DEBUG) {
        System.out.println("Computing frontier for node " + X);
      }
      BitVector DF = new BitVector(ir.getMaxBasicBlockNumber() + 1);
      v.setDominanceFrontier(DF);
      // for each Y in Succ(X) do
      for (BasicBlockEnumeration y = X.getOut(); y.hasMoreElements();) {
        BasicBlock Y = y.next();
        // skip EXIT node
        if (Y.isExit()) {
          continue;
        }
        // if (idom(Y)!=X) then DF(X) <- DF(X) U Y
        if (LTDominatorInfo.getIdom(Y) != X) {
          DF.set(Y.getNumber());
        }
      }
      if (DEBUG) {
        System.out.println("After local " + DF);
      }
      //        for each Z in {idom(z) = X} do
      for (Enumeration<TreeNode> z = tree.getChildren(X); z.hasMoreElements();) {
        DominatorTreeNode zVertex = (DominatorTreeNode) z.nextElement();
        BasicBlock Z = zVertex.getBlock();
        if (DEBUG) {
          System.out.println("Processing Z = " + Z);
        }
        // for each Y in DF(Z) do
        for (BasicBlockEnumeration y = zVertex.domFrontierEnumerator(ir); y.hasMoreElements();) {
          BasicBlock Y = y.next();
          // if (idom(Y)!=X) then DF(X) <- DF(X) U Y
          if (LTDominatorInfo.getIdom(Y) != X) {
            DF.set(Y.getNumber());
          }
        }
      }
      if (DEBUG) {
        System.out.println("After up " + DF);
      }
    }
    if (DEBUG) {
      for (Enumeration<BasicBlock> bbEnum = ir.cfg.basicBlocks(); bbEnum.hasMoreElements();) {
        BasicBlock block = bbEnum.nextElement();
        if (block.isExit()) {
          continue;
        }
        System.out.println(block + " DF: " + tree.getDominanceFrontier(block));
      }
    }
  }

  /**
   * Calculate the dominance frontier for the set of basic blocks
   * represented by a BitVector.
   *
   * <p> NOTE: The dominance frontiers for the IR MUST be calculated
   *    BEFORE calling this routine.
   *
   * @param ir the governing IR
   * @param bits the BitVector representing the set of basic blocks
   * @return a BitVector representing the dominance frontier for the set
   */
  public static BitVector getDominanceFrontier(IR ir, BitVector bits) {
    BitVector result = new BitVector(ir.getMaxBasicBlockNumber() + 1);
    DominatorTree dTree = ir.HIRInfo.dominatorTree;
    for (int i = 0; i < bits.length(); i++) {
      if (bits.get(i)) {
        result.or(dTree.getDominanceFrontier(i));
      }
    }
    return result;
  }

  /**
   * Calculate the iterated dominance frontier for a set of basic blocks
   * represented by a BitVector.
   *
   * <p> NOTE: The dominance frontiers for the IR MUST be calculated
   *    BEFORE calling this routine.
   *
   * @param ir The governing IR
   * @param S  The {@link BitVector} representing the set of basic blocks
   * @return an {@link BitVector} representing the dominance frontier for
   *    the set
   */
  public static BitVector getIteratedDominanceFrontier(IR ir, BitVector S) {
    BitVector DFi = getDominanceFrontier(ir, S);
    while (true) {
      BitVector DFiplus1 = getDominanceFrontier(ir, DFi);
      DFiplus1.or(DFi);
      if (DFi.equals(DFiplus1)) {
        break;
      }
      DFi = DFiplus1;
    }
    return DFi;
  }
}



