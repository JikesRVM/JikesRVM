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

import static org.jikesrvm.compilers.opt.ir.Operators.SPLIT;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.DefUse;
import org.jikesrvm.compilers.opt.OptOptions;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.controlflow.BranchOptimizations;
import org.jikesrvm.compilers.opt.controlflow.DominanceFrontier;
import org.jikesrvm.compilers.opt.controlflow.DominatorsPhase;
import org.jikesrvm.compilers.opt.controlflow.LSTGraph;
import org.jikesrvm.compilers.opt.controlflow.LSTNode;
import org.jikesrvm.compilers.opt.driver.CompilerPhase;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanAtomicElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanCompositeElement;
import org.jikesrvm.compilers.opt.driver.OptimizationPlanElement;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.IRTools;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.RegisterOperandEnumeration;
import org.jikesrvm.compilers.opt.ir.Unary;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.liveness.LiveAnalysis;
import org.jikesrvm.compilers.opt.regalloc.CoalesceMoves;
import org.jikesrvm.compilers.opt.util.GraphNode;
import org.jikesrvm.util.BitVector;


/**
 * Perform live-range splitting.
 *
 * <p>This pass splits live ranges where they enter and exit loop bodies
 * by normal (unexceptional) control flow.
 * It splits a live range for register r by inserting the instruction
 * <code> r = SPLIT r </code>.  Then,  SSA renaming will introduce a new
 * name for r.  The SPLIT operator is later turned into a MOVE during
 * BURS.
 *
 * <p>This pass also splits live ranges on edges to and from infrequent code.
 *
 * <p> This composite phase should be performed at the end of SSA in LIR.
 */
public class LiveRangeSplitting extends OptimizationPlanCompositeElement {

  public final boolean shouldPerform(OptOptions options) {
    return options.SSA_LIVE_RANGE_SPLITTING;
  }

  /**
   * Build this phase as a composite of others.
   */
  public LiveRangeSplitting() {
    super("LIR SSA Live Range Splitting", new OptimizationPlanElement[]{
        // 0. Clean up the IR
        new OptimizationPlanAtomicElement(new BranchOptimizations(2, true, true)),
        new OptimizationPlanAtomicElement(new CoalesceMoves()),
        // 1. Insert the split operations.
        new OptimizationPlanAtomicElement(new LiveRangeSplittingPhase()),
        new OptimizationPlanAtomicElement(new BranchOptimizations(2, true, true)),
        // 2. Use SSA to rename
        new OptimizationPlanAtomicElement(new DominatorsPhase(true)),
        new OptimizationPlanAtomicElement(new DominanceFrontier()),
        new OptimizationPlanAtomicElement(new RenamePreparation()),
        new OptimizationPlanAtomicElement(new EnterSSA()),
        new OptimizationPlanAtomicElement(new LeaveSSA())});
  }

  private static class LiveRangeSplittingPhase extends CompilerPhase {

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    public final boolean shouldPerform(OptOptions options) {
      return options.SSA_LIVE_RANGE_SPLITTING;
    }

    public final String getName() {
      return "Live Range Splitting";
    }

    /**
     * The main entrypoint for this pass.
     */
    public final void perform(IR ir) {
      // 1. Compute an up-to-date loop structure tree.
      DominatorsPhase dom = new DominatorsPhase(true);
      dom.perform(ir);
      LSTGraph lst = ir.HIRInfo.loopStructureTree;
      if (lst == null) {
        throw new OptimizingCompilerException("null loop structure tree");
      }

      // 2. Compute liveness.
      // YUCK: We will later retrieve the live analysis info, relying on the
      // scratchObject field of the Basic Blocks.  Thus, liveness must be
      // computed AFTER the dominators, since the dominator phase also uses
      // the scratchObject field.
      LiveAnalysis live = new LiveAnalysis(false,  // don't create GC maps
                                                   false,  // don't skip (final) local
                                                   // propagation step of live analysis
                                                   false,  // don't store information at handlers
                                                   true);  // skip guards
      live.perform(ir);

      // 3. Perform the analysis
      DefUse.computeDU(ir);
      HashMap<BasicBlockPair, HashSet<Register>> result = findSplitPoints(ir, live, lst);

      // 4. Perform the transformation.
      transform(ir, result);

      // 5. Record that we've destroyed SSA
      if (ir.actualSSAOptions != null) {
        ir.actualSSAOptions.setScalarValid(false);
        ir.actualSSAOptions.setHeapValid(false);
      }
    }

    /**
     * Find the points the IR where live ranges should be split.
     *
     * @param ir the governing IR
     * @param live valid liveness information
     * @param lst a valid loop structure tree
     * @return the result as a mapping from BasicBlockPair to a set of registers
     */
    private static HashMap<BasicBlockPair, HashSet<Register>> findSplitPoints(IR ir, LiveAnalysis live,
                                                                                  LSTGraph lst) {

      HashMap<BasicBlockPair, HashSet<Register>> result = new HashMap<BasicBlockPair, HashSet<Register>>(10);
      for (Enumeration<GraphNode> e = lst.enumerateNodes(); e.hasMoreElements();) {
        LSTNode node = (LSTNode) e.nextElement();
        BasicBlock header = node.getHeader();
        BitVector loop = node.getLoop();
        if (loop == null) continue;

        // First split live ranges on edges coming into the loop header.
        for (Enumeration<BasicBlock> in = header.getIn(); in.hasMoreElements();) {
          BasicBlock bb = in.nextElement();
          if (loop.get(bb.getNumber())) continue;
          HashSet<Register> liveRegisters = live.getLiveRegistersOnEdge(bb, header);
          for (Register r : liveRegisters) {
            if (r.isSymbolic()) {
              HashSet<Register> s = findOrCreateSplitSet(result, bb, header);
              s.add(r);
            }
          }
        }

        // Next split live ranges on every normal exit from the loop.
        for (int i = 0; i < loop.length(); i++) {
          if (loop.get(i)) {
            BasicBlock bb = ir.getBasicBlock(i);
            for (Enumeration<BasicBlock> out = bb.getNormalOut(); out.hasMoreElements();) {
              BasicBlock dest = out.nextElement();
              if (loop.get(dest.getNumber())) continue;
              HashSet<Register> liveRegisters = live.getLiveRegistersOnEdge(bb, dest);
              for (Register r : liveRegisters) {
                if (r.isSymbolic()) {
                  HashSet<Register> s = findOrCreateSplitSet(result, bb, dest);
                  s.add(r);
                }
              }
            }
          }
        }
      }

      addEntriesForInfrequentBlocks(ir, live, result);

      return result;
    }

    /**
     * Split live ranges on entry and exit to infrequent regions.
     * Add this information to 'result', a mapping from BasicBlockPair to a set of
     * registers to split.
     *
     * @param ir the governing IR
     * @param live valid liveness information
     * @param result mapping from BasicBlockPair to a set of registers
     */
    private static void addEntriesForInfrequentBlocks(IR ir, LiveAnalysis live,
                                                      HashMap<BasicBlockPair, HashSet<Register>> result) {
      for (Enumeration<BasicBlock> e = ir.getBasicBlocks(); e.hasMoreElements();) {
        BasicBlock bb = e.nextElement();
        boolean bbInfrequent = bb.getInfrequent();
        for (Enumeration<BasicBlock> out = bb.getNormalOut(); out.hasMoreElements();) {
          BasicBlock dest = out.nextElement();
          boolean destInfrequent = dest.getInfrequent();
          if (bbInfrequent ^ destInfrequent) {
            HashSet<Register> liveRegisters = live.getLiveRegistersOnEdge(bb, dest);
            for (Register r : liveRegisters) {
              if (r.isSymbolic()) {
                HashSet<Register> s = findOrCreateSplitSet(result, bb, dest);
                s.add(r);
              }
            }
          }
        }
      }
    }

    /**
     * Given a mapping from BasicBlockPair -> HashSet, find or create the hash
     * set corresponding to a given basic block pair
     *
     * @param map the mapping to search
     * @param b1 the first basic block in the pair
     * @param b2 the second basic block in the pair
     */
    private static HashSet<Register> findOrCreateSplitSet(HashMap<BasicBlockPair, HashSet<Register>> map,
                                                              BasicBlock b1, BasicBlock b2) {
      BasicBlockPair pair = new BasicBlockPair(b1, b2);
      HashSet<Register> set = map.get(pair);
      if (set == null) {
        set = new HashSet<Register>(5);
        map.put(pair, set);
      }
      return set;
    }

    /**
     * Perform the transformation
     *
     * @param ir the governing IR
     * @param xform a mapping from BasicBlockPair to the set of registers
     * to split
     */
    private static void transform(IR ir, HashMap<BasicBlockPair, HashSet<Register>> xform) {
      for (Map.Entry<BasicBlockPair, HashSet<Register>> entry : xform.entrySet()) {
        BasicBlockPair bbp = entry.getKey();
        HashSet<Register> toSplit = entry.getValue();

        // we go ahead and split all edges, instead of just critical ones.
        // we'll clean up the mess later after SSA.
        BasicBlock target = IRTools.makeBlockOnEdge(bbp.src, bbp.dest, ir);
        SSA.replaceBlockInPhis(bbp.dest, bbp.src, target);

        for (Register r : toSplit) {
          if (r.defList == null) continue;
          Instruction s = null;
          switch (r.getType()) {
            case Register.ADDRESS_TYPE:
              RegisterOperand lhs2 = IRTools.A(r);
              RegisterOperand rhs2 = IRTools.A(r);
              s = Unary.create(SPLIT, lhs2, rhs2);
              // fix up types: only split live ranges when the type is
              // consistent at all defs
              TypeReference t2 = null;
              RegisterOperandEnumeration e2 = DefUse.defs(r);
              if (!e2.hasMoreElements()) {
                s = null;
              } else {
                RegisterOperand rop2 = e2.next();
                t2 = rop2.getType();
                while (e2.hasMoreElements()) {
                  RegisterOperand nextOp2 = e2.next();
                  if (nextOp2.getType() != t2) {
                    s = null;
                  }
                }
              }
              if (s != null) {
                lhs2.setType(t2);
                rhs2.setType(t2);
              }
              break;
            case Register.INTEGER_TYPE:
              RegisterOperand lhs = IRTools.I(r);
              RegisterOperand rhs = IRTools.I(r);
              s = Unary.create(SPLIT, lhs, rhs);
              // fix up types: only split live ranges when the type is
              // consistent at all defs
              TypeReference t = null;
              RegisterOperandEnumeration e = DefUse.defs(r);
              if (!e.hasMoreElements()) {
                s = null;
              } else {
                RegisterOperand rop = e.next();
                t = rop.getType();
                while (e.hasMoreElements()) {
                  RegisterOperand nextOp = e.next();
                  if (nextOp.getType() != t) {
                    s = null;
                  }
                }
              }
              if (s != null) {
                lhs.setType(t);
                rhs.setType(t);
              }
              break;
            case Register.FLOAT_TYPE:
              s = Unary.create(SPLIT, IRTools.F(r), IRTools.F(r));
              break;
            case Register.DOUBLE_TYPE:
              s = Unary.create(SPLIT, IRTools.D(r), IRTools.D(r));
              break;
            case Register.LONG_TYPE:
              s = Unary.create(SPLIT, IRTools.L(r), IRTools.L(r));
              break;
            default:
              // we won't split live ranges for other types.
              s = null;
              break;
          }
          if (s != null) {
            target.prependInstruction(s);
          }
        }
      }
    }

    /**
     * A utility class to represent an edge in the CFG.
     */
    private static class BasicBlockPair {
      /**
       * The source of a control-flow edge
       */
      final BasicBlock src;

      /**
       * The sink of a control-flow edge
       */
      final BasicBlock dest;

      BasicBlockPair(BasicBlock src, BasicBlock dest) {
        this.src = src;
        this.dest = dest;
      }

      static int nextHash = 0;
      int myHash = ++nextHash;

      public int hashCode() {
        return myHash;
      }

      public boolean equals(Object o) {
        if (!(o instanceof BasicBlockPair)) return false;
        BasicBlockPair p = (BasicBlockPair) o;
        return (src.equals(p.src) && dest.equals(p.dest));
      }

      public String toString() {
        return "<" + src + "," + dest + ">";
      }
    }
  }

  /**
   * This class sets up the IR state prior to entering SSA.
   */
  private static class RenamePreparation extends CompilerPhase {

    /**
     * Return this instance of this phase. This phase contains no
     * per-compilation instance fields.
     * @param ir not used
     * @return this
     */
    public CompilerPhase newExecution(IR ir) {
      return this;
    }

    public final boolean shouldPerform(OptOptions options) {
      return options.SSA_LIVE_RANGE_SPLITTING;
    }

    public final String getName() {
      return "Rename Preparation";
    }

    /**
     * register in the IR the SSA properties we need for simple scalar
     * renaming
     */
    public final void perform(IR ir) {
      ir.desiredSSAOptions = new SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
    }
  }
}
