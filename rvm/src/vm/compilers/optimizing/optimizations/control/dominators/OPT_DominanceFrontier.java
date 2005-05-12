/*
 * (C) Copyright IBM Corp. 2001, 2004
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

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
 *
 * @author Stephen Fink
 */
class OPT_DominanceFrontier extends OPT_CompilerPhase {
  final static boolean DEBUG = false;

  /**
   * Should this phase be performed?  This is a member of a composite
   * phase, so always return true.  The parent composite phase will
   * dictate.
   * @param options controlling compiler options
   */
  public final boolean shouldPerform(OPT_Options options) {
    return true;
  }

  /**
   * Return a String representation for this phase
   * @return a String representation for this phase
   */
  public final String getName() {
    return  "Dominance Frontier";
  }

  /**
   * Should the IR be printed either before or after performing this phase?
   *
   * @param options controlling compiler options
   * @param before true iff querying before the phase
   * @return true or false
   */
  public final boolean printingEnabled(OPT_Options options, boolean before) {
    return  false;
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
  public void perform(OPT_IR ir) {
    // make sure the dominator computation completed successfully
    if (!ir.HIRInfo.dominatorsAreComputed)
      return;
    // make sure dominator tree is computed
    if (ir.HIRInfo.dominatorTree == null)
      return;
    // for each X in a bottom-up traversal of the dominator tree do
    OPT_DominatorTree tree = ir.HIRInfo.dominatorTree;
    for (Enumeration x = tree.getBottomUpEnumerator(); x.hasMoreElements();) {
      OPT_DominatorTreeNode v = (OPT_DominatorTreeNode)x.nextElement();
      OPT_BasicBlock X = v.getBlock();
      if (DEBUG)
        System.out.println("Computing frontier for node " + X);
      OPT_BitVector DF = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
      v.setDominanceFrontier(DF);
      // for each Y in Succ(X) do
      for (OPT_BasicBlockEnumeration y = X.getOut(); y.hasMoreElements();) {
        OPT_BasicBlock Y = y.next();
        // skip EXIT node
        if (Y.isExit())
          continue;
        // if (idom(Y)!=X) then DF(X) <- DF(X) U Y
        if (OPT_LTDominatorInfo.getIdom(Y) != X)
          DF.set(Y.getNumber());
      }
      if (DEBUG)
        System.out.println("After local " + DF);
      //        for each Z in {idom(z) = X} do
      for (Enumeration z = tree.getChildren(X); z.hasMoreElements();) {
        OPT_DominatorTreeNode zVertex = (OPT_DominatorTreeNode)z.nextElement();
        OPT_BasicBlock Z = zVertex.getBlock();
        if (DEBUG)
          System.out.println("Processing Z = " + Z);
        // for each Y in DF(Z) do
        for (OPT_BasicBlockEnumeration y = 
            zVertex.domFrontierEnumerator(ir); y.hasMoreElements();) {
          OPT_BasicBlock Y = y.next();
          // if (idom(Y)!=X) then DF(X) <- DF(X) U Y
          if (OPT_LTDominatorInfo.getIdom(Y) != X)
            DF.set(Y.getNumber());
        }
      }
      if (DEBUG)
        System.out.println("After up " + DF);
    }
    if (DEBUG) {
      for (Enumeration bbEnum = ir.cfg.nodes(); bbEnum.hasMoreElements();) {
        OPT_BasicBlock block = (OPT_BasicBlock)bbEnum.nextElement();
        if (block.isExit())
          continue;
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
  public static OPT_BitVector getDominanceFrontier(OPT_IR ir, OPT_BitVector bits) {
    OPT_BitVector result = new OPT_BitVector(ir.getMaxBasicBlockNumber()+1);
    OPT_DominatorTree dTree = ir.HIRInfo.dominatorTree;
    for (int i = 0; i < bits.length(); i++) {
      if (bits.get(i)) {
        result.or(dTree.getDominanceFrontier(i));
      }
    }
    return  result;
  }

  /** 
   * Calculate the iterated dominance frontier for a set of basic blocks
   * represented by a BitVector.
   *
   * <p> NOTE: The dominance frontiers for the IR MUST be calculated 
   *    BEFORE calling this routine.
   *
   * @param ir The governing IR
   * @param S  The {@link OPT_BitVector} representing the set of basic blocks
   * @return an {@link OPT_BitVector} representing the dominance frontier for
   *    the set 
   */
  public static OPT_BitVector getIteratedDominanceFrontier(OPT_IR ir, 
                                                           OPT_BitVector S) {
    OPT_BitVector DFi = getDominanceFrontier(ir, S);
    while (true) {
      OPT_BitVector DFiplus1 = getDominanceFrontier(ir, DFi);
      DFiplus1.or(DFi);
      if (DFi.equals(DFiplus1))
        break;
      DFi = DFiplus1;
    }
    return  DFi;
  }
}



