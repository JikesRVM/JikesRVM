/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Enumeration;
import java.util.Iterator;

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
 *
 * @author Stephen Fink
 */
class OPT_LiveRangeSplitting extends OPT_OptimizationPlanCompositeElement {

  public final boolean shouldPerform (OPT_Options options) {
    return options.LIVE_RANGE_SPLITTING;
  }

  /**
   * Build this phase as a composite of others.
   */
  OPT_LiveRangeSplitting() {
    super("LIR SSA Live Range Splitting", new OPT_OptimizationPlanElement[] {
          // 0. Clean up the IR
          new OPT_OptimizationPlanAtomicElement(new OPT_BranchOptimizations(2, true, true)),
          new OPT_OptimizationPlanAtomicElement(new OPT_CoalesceMoves()),
          // 1. Insert the split operations.
          new OPT_OptimizationPlanAtomicElement(new LiveRangeSplitting()),
          new OPT_OptimizationPlanAtomicElement(new OPT_BranchOptimizations(2, true, true)),
          // 2. Use SSA to rename
          new OPT_OptimizationPlanAtomicElement(new OPT_DominatorsPhase(true)), 
          new OPT_OptimizationPlanAtomicElement(new OPT_DominanceFrontier()),
          new OPT_OptimizationPlanAtomicElement(new RenamePreparation()),
          new OPT_OptimizationPlanAtomicElement(new OPT_EnterSSA()),
          new OPT_OptimizationPlanAtomicElement(new OPT_LeaveSSA())
          });
  }

  private static class LiveRangeSplitting extends OPT_CompilerPhase 
    implements OPT_Operators{

    public final boolean shouldPerform (OPT_Options options) {
      return options.LIVE_RANGE_SPLITTING;
    }

    public final String getName () {
      return "Live Range Splitting";
    }

    /**
     * The main entrypoint for this pass.
     */
    public final void perform(OPT_IR ir) {
      // 1. Compute an up-to-date loop structure tree.
      OPT_DominatorsPhase dom = new OPT_DominatorsPhase(true);
      dom.perform(ir);
      OPT_LSTGraph lst = ir.HIRInfo.LoopStructureTree;
      if (lst == null) {
        throw new OPT_OptimizingCompilerException("null loop structure tree");
      }

      // 2. Compute liveness.
      // YUCK: We will later retrieve the live analysis info, relying on the
      // scratchObject field of the Basic Blocks.  Thus, liveness must be 
      // computed AFTER the dominators, since the dominator phase also uses
      // the scratchObject field.
      OPT_LiveAnalysis live = 
        new OPT_LiveAnalysis(false,  // don't create GC maps
                             false,  // don't skip (final) local 
                                     // propagation step of live analysis
                             false,  // don't store information at handlers
                             true);  // skip guards
      live.perform(ir);

      // 3. Perform the analysis
      OPT_DefUse.computeDU(ir);
      HashMap result = findSplitPoints(ir,live,lst);

      // 4. Perform the transformation.
      transform(ir,result);

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
    private static HashMap findSplitPoints(OPT_IR ir, OPT_LiveAnalysis live,
                                           OPT_LSTGraph lst) {

      HashMap result = new HashMap(10);
      for (Enumeration e = lst.enumerateNodes(); e.hasMoreElements(); ) {
        OPT_LSTNode node = (OPT_LSTNode)e.nextElement();
        OPT_BasicBlock header = node.getHeader();
        OPT_BitVector loop = node.getLoop();
        if (loop == null) continue;

        // First split live ranges on edges coming into the loop header.
        for (Enumeration in = header.getIn(); in.hasMoreElements(); ) {
          OPT_BasicBlock bb = (OPT_BasicBlock)in.nextElement();
          if (loop.get(bb.getNumber())) continue;
          HashSet liveRegisters = live.getLiveRegistersOnEdge(bb,header);
          for (Iterator i = liveRegisters.iterator(); i.hasNext();) {
            OPT_Register r = (OPT_Register)i.next();
            if (r.isSymbolic()) {
              HashSet s = findOrCreateSplitSet(result,bb,header);
              s.add(r);
            }
          }
        }

        // Next split live ranges on every normal exit from the loop.
        for (int i=0; i<loop.length(); i++) {
          if (loop.get(i)) {
            OPT_BasicBlock bb = ir.getBasicBlock(i);
            for (Enumeration out = bb.getNormalOut(); out.hasMoreElements(); ){
              OPT_BasicBlock dest = (OPT_BasicBlock)out.nextElement();
              if (loop.get(dest.getNumber())) continue;
              HashSet liveRegisters = live.getLiveRegistersOnEdge(bb,dest);
              for (Iterator it = liveRegisters.iterator(); it.hasNext();) {
                OPT_Register r = (OPT_Register)it.next();
                if (r.isSymbolic()) {
                  HashSet s = findOrCreateSplitSet(result,bb,dest);
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
    private static void addEntriesForInfrequentBlocks(OPT_IR ir, OPT_LiveAnalysis live,
                                                      HashMap result) {
      for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
        OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
        boolean bbInfrequent = bb.getInfrequent();
        for (Enumeration out = bb.getNormalOut(); out.hasMoreElements(); ) {
          OPT_BasicBlock dest = (OPT_BasicBlock)out.nextElement();
          boolean destInfrequent = dest.getInfrequent();
          if (bbInfrequent ^ destInfrequent) {
            HashSet liveRegisters = live.getLiveRegistersOnEdge(bb,dest);
            for (Iterator it = liveRegisters.iterator(); it.hasNext();) {
              OPT_Register r = (OPT_Register)it.next();
              if (r.isSymbolic()) {
                HashSet s = findOrCreateSplitSet(result,bb,dest);
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
    private static HashSet findOrCreateSplitSet(HashMap map, OPT_BasicBlock b1, 
                                                OPT_BasicBlock b2) {
      HashSet set = (HashSet)map.get(new BasicBlockPair(b1,b2));
      if (set == null) {
        set = new HashSet(5);
        map.put(new BasicBlockPair(b1,b2), set);
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
    private static void transform(OPT_IR ir, HashMap xform) {
      for (Iterator i = xform.keySet().iterator(); i.hasNext(); ) {
        BasicBlockPair bbp = (BasicBlockPair)i.next();
        HashSet toSplit = (HashSet)xform.get(bbp);
        
        // we go ahead and split all edges, instead of just critical ones.
        // we'll clean up the mess later after SSA.
        OPT_BasicBlock target = OPT_IRTools.makeBlockOnEdge(bbp.src,
                                                            bbp.dest,ir);
        OPT_SSA.replaceBlockInPhis(bbp.dest,bbp.src,target);
        
        for (Iterator splits = toSplit.iterator(); splits.hasNext(); ) {
          OPT_Register r = (OPT_Register)splits.next();
          if (r.defList == null) continue;
          OPT_Instruction s = null;
          switch (r.getType()) {
            case OPT_Register.INTEGER_TYPE:
              OPT_RegisterOperand lhs = OPT_IRTools.I(r);
              OPT_RegisterOperand rhs = OPT_IRTools.I(r);
              s = Unary.create(SPLIT,lhs,rhs);
              // fix up types: only split live ranges when the type is
              // consistent at all defs
              VM_TypeReference t = null;
              OPT_RegisterOperandEnumeration e = OPT_DefUse.defs(r);
              if (!e.hasMoreElements()) {
                s = null;
              } else {
                OPT_RegisterOperand rop = e.next();
                t = rop.type;
                while (e.hasMoreElements()) {
                  OPT_RegisterOperand nextOp = e.next();
                  if (nextOp.type != t) {
                    s = null;
                  }
                }
              }
              if (s != null) {
                lhs.type = t;
                rhs.type = t;
              }
              break;
            case OPT_Register.FLOAT_TYPE:
              s = Unary.create(SPLIT,OPT_IRTools.F(r), OPT_IRTools.F(r));
              break;
            case OPT_Register.DOUBLE_TYPE:
              s = Unary.create(SPLIT,OPT_IRTools.D(r), OPT_IRTools.D(r));
              break;
            case OPT_Register.LONG_TYPE:
              s = Unary.create(SPLIT,OPT_IRTools.L(r), OPT_IRTools.L(r));
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
      OPT_BasicBlock src;

      /**
       * The sink of a control-flow edge
       */
      OPT_BasicBlock dest;

      BasicBlockPair(OPT_BasicBlock src, OPT_BasicBlock dest) {
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
        BasicBlockPair p = (BasicBlockPair)o;
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
  private static class RenamePreparation extends OPT_CompilerPhase {

    public final boolean shouldPerform (OPT_Options options) {
      return options.LIVE_RANGE_SPLITTING;
    }

    public final String getName () {
      return  "Rename Preparation";
    }

    /**
     * register in the IR the SSA properties we need for simple scalar
     * renaming
     */
    final public void perform(OPT_IR ir) {
      ir.desiredSSAOptions = new OPT_SSAOptions();
      ir.desiredSSAOptions.setScalarsOnly(true);
      ir.desiredSSAOptions.setBackwards(false);
      ir.desiredSSAOptions.setInsertUsePhis(false);
    }
  }
}
