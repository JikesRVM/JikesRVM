/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.*;

/**
 * Estimate basic block frequencies based on the branch probabilities.
 * 
 * @author Steve Fink
 * @author Dave Grove
 */
class OPT_EstimateBlockFrequencies extends OPT_CompilerPhase {

  int optLevel;
  OPT_IR ir;
  OPT_LSTGraph lst;
  OPT_BasicBlock[] topOrder;

  OPT_EstimateBlockFrequencies(int ol) {
    optLevel = ol;
  }

  String getName () { return  "Estimate Block Frequencies"; }

  boolean shouldPerform (OPT_Options options) {
    return options.getOptLevel() >= optLevel;
  }

  /**
   * Do simplistic static splitting to create hot traces
   * with that do not have incoming edges from 
   * blocks that are statically predicted to be cold.
   * 
   * @param _ir the IR on which to apply the phase
   */
  void perform (OPT_IR _ir) {
    // Prepare 
    ir = _ir;
    ir.cfg.compactNodeNumbering();
    ir.cfg.resetTopSorted();
    ir.cfg.buildTopSort();
    topOrder = new OPT_BasicBlock[ir.cfg.numberOfNodes()];
    int idx = 0;
    for (OPT_BasicBlock ptr = ir.cfg.entry();
	 ptr != null;
	 ptr = (OPT_BasicBlock)ptr.getForwardSortedNext()) {
      topOrder[idx++] = ptr;
      ptr.setExecutionFrequency(0f);
      ptr.clearScratchFlag();
    }

    // Compute dominator information and lst.
    OPT_LTDominators.approximate(ir, true);
    OPT_DominatorTree.perform(ir, true);
    OPT_LSTGraph.perform(ir);
    lst = ir.HIRInfo.LoopStructureTree;

    // Compute loop multipliers
    if (lst != null) {
      computeLoopMultipliers(lst.getRoot());
      for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks();
	   e.hasMoreElements();) {
	OPT_BasicBlock bb = e.next();
	bb.setExecutionFrequency(0f);
	bb.clearScratchFlag();
      }
    }

    // Compute execution frequency of each basic block
    computeBlockFrequencies();
  }

  private void computeLoopMultipliers(OPT_LSTNode n) {
    // Compute multipliers and loop exit
    for (Enumeration e = n.getChildren(); e.hasMoreElements();) {
      computeLoopMultipliers((OPT_LSTNode)e.nextElement());
    }
    if (n != lst.getRoot()) {
      computeMultiplier(n);
      n.header.clearScratchFlag(); // so we won't ignore when processing enclosing loop
    }
  }


  /**
   * Compute the loop multiplier for this loop nest
   */
  private void computeMultiplier(OPT_LSTNode n) {
    n.initializeLoopExits();
    computeNodeWeights(n);
    float loopExitWeight = computeLoopExitWeight(n);
    if (loopExitWeight == 0f) { 
      // loop with no normal exits
      loopExitWeight = 0.2f; // loop executes 5 times.
    }
    n.loopMultiplier = 1.0f / loopExitWeight;
  }
  
  /**
   * Propagate execution frequencies through the loop.
   * Also records loop exit edges in loopExits.
   */
  private void computeNodeWeights(OPT_LSTNode n) {
    n.header.setExecutionFrequency(1f);
    int idx = 0;
    while (topOrder[idx] != n.header) idx++;
    for (int numNodes = n.loop.populationCount(); numNodes > 0;) {
      OPT_BasicBlock cur = topOrder[idx++];
      if (!n.loop.get(cur.getNumber())) continue; // node was not in the loop nest being processed.
      OPT_LSTNode other = lst.getLoop(cur);
      if (other != n) {
	if (cur == other.header) {
	  // loop header of nested loop
	  numNodes -= other.loop.populationCount();
	}
	continue; // skip over nodes in nested loop.
      }

      numNodes--;
      cur.setScratchFlag();
      float weight = cur.getExecutionFrequency();
      for (OPT_WeightedBranchTargets wbt = new OPT_WeightedBranchTargets(cur);
	   wbt.hasMoreElements(); wbt.advance()) {
	processEdge(n, cur, wbt.curBlock(), wbt.curWeight(), weight);
      }
    }
  }

  private void processEdge(OPT_LSTNode n, 
			   OPT_BasicBlock source, 
			   OPT_BasicBlock target, 
			   float prob, 
			   float weight) {
    if (target.getScratchFlag()) return; // ignore backedge
    if (n.loop.get(target.getNumber())) {
      OPT_LSTNode other = lst.getLoop(target);
      if (other == n) {
	target.augmentExecutionFrequency(prob * weight);
      } else {
	// header of nested loop; pass prob and weight through to targets of loop exits
	// Algorithm: find the total loopExitWeight, then distribute prob and weight
	//            in ratio to the exit weight for each exit.
	//            Effectively we are treating the nested loop as an n-way branch to its loop exits.
	float exitWeight = computeLoopExitWeight(other);
	for (Iterator i = other.loopExits.iterator(); i.hasNext();) {
	  OPT_LSTNode.Edge exit = (OPT_LSTNode.Edge)i.next();
	  float myWeight = exit.source.getExecutionFrequency() * exit.probability;
	  float myFraction = myWeight/exitWeight;
	  processEdge(n, source, exit.target, prob * myFraction, weight);
	}
      }
    } else {
      n.addLoopExit(source, target, prob);
    }
  }

  private float computeLoopExitWeight(OPT_LSTNode n) {
    float exitWeight = 0f;
    for (Iterator i = n.loopExits.iterator(); i.hasNext();) {
      OPT_LSTNode.Edge exit = (OPT_LSTNode.Edge)i.next();
      exitWeight += (exit.source.getExecutionFrequency() * exit.probability);
    }
    return exitWeight;
  }


  private void computeBlockFrequencies() {
    ir.cfg.entry().setExecutionFrequency(1f);
    for (int idx =0; idx<topOrder.length; idx++) {
      OPT_BasicBlock cur = topOrder[idx];
      if (cur == null || cur.isExit()) continue; // ignore exit node.
      if (lst != null) {
	OPT_LSTNode loop = lst.getLoop(cur);
	if (loop != null && loop.header == cur) {
	  cur.setExecutionFrequency(cur.getExecutionFrequency() * loop.loopMultiplier);
	}
      }
      float weight = cur.getExecutionFrequency();
      cur.setScratchFlag();


      for (OPT_WeightedBranchTargets wbt = new OPT_WeightedBranchTargets(cur);
	   wbt.hasMoreElements(); wbt.advance()) {
	processEdge2(cur, wbt.curBlock(), wbt.curWeight(), weight);
      }
    }
  }

  private void processEdge2(OPT_BasicBlock source, 
			    OPT_BasicBlock target, 
			    float prob, 
			    float weight) {
    if (target.getScratchFlag()) return; // ignore backedge
    target.augmentExecutionFrequency(prob * weight);
  }
}
