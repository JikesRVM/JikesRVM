/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

/**
 * Derive relative basic block execution frequencies from branch probabilities.<p>
 * 
 * This code assumes that the loop structure tree can be constructed for
 * the CFG in question.  This implies that the CFG is reducible. <p>
 * 
 * The basic algorithm is as follows:
 * <ul>
 * <li> Construct the loop structure tree for the CFG. </li>
 * <li> In a postorder traversal, compute the loop multiplier for each loop.
 *      The loop multiplier is a number such that the execution frequency of 
 *      the loop pre-header times the loop multiplier is equal to the 
 *      execution frequency of the loop head.  This can be derived by computing
 *      the loop exit weight (the probability of exiting the loop) and applying
 *      Kirchoff's law that flow in is equal to flow out.  Loop exit weight
 *      can be computed in a single topological (ignoring backedges) traversal
 *      of the nodes in the loop. </li>
 * <li> Assign the entry node weight 1.  In a topological traversal of the CFG
 *      (ignoring backedges), propagate the weights.  When processing a loop head,
 *      multiply the incoming weight by the loop multiplier.</li>
 * </ul>
 *
 * @author Steve Fink
 * @author Dave Grove
 */
class OPT_EstimateBlockFrequencies extends OPT_CompilerPhase {

  /**
   * The IR on which to operate.
   */
  private OPT_IR ir;

  /**
   * The loop structure tree of said IR
   */
  private OPT_LSTGraph lst;

  /**
   * Topological ordering (ignoring backedges) of CFG
   */
  private OPT_BasicBlock[] topOrder;

  public String getName() { return  "Estimate Block Frequencies"; }

  public void reportAdditionalStats() {
    VM.sysWrite("  ");
    VM.sysWrite(container.counter1/container.counter2*100, 2);
    VM.sysWrite("% Infrequent BBs");
  }

  /**
   * Compute relative basic block frequencies for the argument IR based on the
   * branch probability information on each conditional and multiway branch.
   * Assumptions: (1) LST is valid
   *              (2) basic block numbering is dense (compact has been called).
   * @param _ir the IR on which to apply the phase
   */
  public void perform(OPT_IR _ir) {
    // Prepare 
    ir = _ir;

    if (ir.options.FREQUENCY_STRATEGY == OPT_Options.DUMB_FREQ) {
      setDumbFrequencies(ir);
      return;
    }

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

    // Get pre-computed LST from IR.
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

    // Set infrequent bits on basic blocks
    computeInfrequentBlocks(ir);
  }

  /**
   * Set the frequency of each basic block to 1.0f.
   */
  private void setDumbFrequencies(OPT_IR ir) {
    for (OPT_BasicBlockEnumeration e = ir.getBasicBlocks(); e.hasMoreElements();) {
      OPT_BasicBlock bb = e.next();
      bb.setExecutionFrequency(1f);
    }
  }
  /**
   * Compute which blocks are infrequent.
   * Algorithm: let f = INFREQUENT_THRESHOLD.
   * Start with S = {all basic blocks}.
   * Sort the blocks by frequency.  Starting with the most frequent
   * blocks, remove blocks from S until the sum of block frequencies in S
   * <= f.  Then blocks in S are infrequent.
   *
   * @param ir the governing IR.
   */
  private void computeInfrequentBlocks(OPT_IR ir) {
    int i = 0;
    float[] freq = new float[ir.getMaxBasicBlockNumber()];
    float total = 0f;
    // count the total frequency of all blocks
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      freq[i]= bb.getExecutionFrequency();  
      total += freq[i];
      i++;
    }
    // sort the frequencies (ascending);
    Arrays.sort(freq);
    float f = ir.options.INFREQUENT_THRESHOLD;
    float goal = (1f-f)*total;
    total = 0f;
    float threshold = 0f;
    // add up the frequencies (desceding) until we real the goal.
    for (i = freq.length-1; i>=0 && total<goal; i--) {
      threshold = freq[i];
      total += threshold;
    }
    // go back and set infrequent bits.
    for (Enumeration e = ir.getBasicBlocks(); e.hasMoreElements(); ) {
      OPT_BasicBlock bb = (OPT_BasicBlock)e.nextElement();
      if (bb.getExecutionFrequency() < threshold) {
        bb.setInfrequent();
        container.counter1++;
      } else {
        bb.clearInfrequent();
      }
      container.counter2++;
    }
  }
  
  /**
   * Postorder traversal of LST computing loop multiplier and loop exits 
   * for each loop.
   */
  private void computeLoopMultipliers(OPT_LSTNode n) {
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
      if (idx >= topOrder.length) {
        numNodes--;
        continue;
      }
      OPT_BasicBlock cur = topOrder[idx++];
      if (cur == null) {
        numNodes--;
        continue;
      }
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
    // Kludge: if we think the loop has no exits, lets pretend that there is a 1%
    //         chance of exiting to avoid getting NaN's in our computation.
    return exitWeight == 0f ? 0.01f : exitWeight;
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
        OPT_BasicBlock target = wbt.curBlock();
        if (!target.getScratchFlag()) {
          target.augmentExecutionFrequency(wbt.curWeight() * weight);
        }
      }
    }
  }
}
