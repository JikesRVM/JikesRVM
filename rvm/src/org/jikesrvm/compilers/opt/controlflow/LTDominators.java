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

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OperationNotImplementedException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.util.Stack;

/**
 * Calculate dominators using Langauer and Tarjan's fastest algorithm.
 * TOPLAS 1(1), July 1979.  This implementation uses path compression and
 * results in a O(e * alpha(e,n)) complexity, where e is the number of
 * edges in the CFG and n is the number of nodes.
 * <p>
 * Sources: TOPLAS article, Muchnick book
 * <p>
 * The current implementation (4/25/00) does not include the EXIT node
 * in any solution despite the fact that it is part of the CFG (it has
 * incoming edges).  This is to be compatible with the old code.
 */
public class LTDominators extends Stack<BasicBlock> {
  static final boolean DEBUG = false;

  /**
   * Indicates whether we perform the algorithm over the CFG or
   *  the reverse CFG, i.e., whether we are computing dominators or
   *  post-dominators.
   */
  private final boolean forward;

  /**
   * a counter for assigning DFS numbers
   */
  protected int DFSCounter;

  /**
   * a mapping from DFS number to their basic blocks
   */
  private BasicBlock[] vertex;

  /**
   * a convenient place to locate the cfg to avoid passing it internally
   */
  private final ControlFlowGraph cfg;

  /**
   * The constructor, called by the perform method
   * @param ir
   * @param forward Should we compute regular dominators, or post-dominators?
   */
  LTDominators(IR ir, boolean forward) {
    cfg = ir.cfg;               // save the cfg for easy access
    this.forward = forward;     // save the forward flag
  }

  /**
   * The entry point for this phase
   * @param ir the IR
   * @param forward Should we compute regular dominators, or post-dominators?
   * @param unfactor Should we unfactor the CFG?
   */
  public static void perform(IR ir, boolean forward, boolean unfactor) {
    if (ir.hasReachableExceptionHandlers()) {
      if (unfactor) {
        ir.unfactor();
      } else {
        throw new OperationNotImplementedException("IR with exception handlers");
      }
    }
    LTDominators dom = new LTDominators(ir, forward);
    dom.analyze(ir);
  }

  /**
   * Compute approximate dominator/post dominator without unfactoring
   * exception handlers.  Can only be used if what the client wants is
   * approximate domination (ie, if it doesn't actually have to be correct...)
   * @param ir the IR
   * @param forward Should we compute regular dominators, or post-dominators?
   */
  public static void approximate(IR ir, boolean forward) {
    LTDominators dom = new LTDominators(ir, forward);
    dom.analyze(ir);
  }

  /**
   * analyze dominators
   */
  protected void analyze(IR ir) {
    if (DEBUG) {
      System.out.println("   Here's the CFG for method: " + ir.method.getName() + "\n" + ir.cfg);
    }

    // Step 1: Perform a DFS numbering
    step1();

    // Check to make sure all nodes were reached
    checkReachability(ir);

    // Step 2: the heart of the algorithm
    step2();

    // Step 3: adjust immediate dominators of nodes whoe current version of
    //    the immediate dominators differs from the nodes with the depth-first
    //    number of the node's semidominator.
    step3();

    if (DEBUG) {
      printResults(ir);
    }
  }

  /**
   * Check to make sure all nodes were reached
   */
  private void checkReachability(IR ir) {
    if (!forward) {
      if (DFSCounter != cfg.numberOfNodes()) {
        VM.sysWrite(" *** Warning ***\n CFG for method " +
                    ir.method.getName() +
                    " in class " +
                    ir.method.getDeclaringClass() +
                    " has unreachable nodes.\n");
        VM.sysWrite(" Assuming pessimistic results in dominators computation\n" + " for unreachable nodes.\n");
      }
    }
  }

  /**
   *  The goal of this step is to perform a DFS numbering on the CFG,
   *  starting at the root.  The exit node is not included.
   */
  private void step1() {
    // allocate the vertex array, one element for each basic block, starting
    // at 1
    vertex = new BasicBlock[cfg.numberOfNodes() + 1];
    DFSCounter = 0;
    if (DEBUG) { System.out.println("Initializing blocks:"); }

    // Initialize each block with an empty set of predecessors and
    // a 0 for a semidominator
    for (Enumeration<BasicBlock> bbEnum = cfg.basicBlocks(); bbEnum.hasMoreElements();) {
      BasicBlock block = bbEnum.nextElement();
      // We don't compute a result for the exit node in the forward direction
      if (!forward || !block.isExit()) {
        block.scratchObject = new LTDominatorInfo(block);
        if (DEBUG) {
          printNextNodes(block);
        }
      }
    }

    DFS();

    if (DEBUG) {
      System.out.println("DFSCounter: " + DFSCounter + ", CFG Nodes: " + cfg.numberOfNodes());
      printDFSNumbers();
    }
  }

  private void DFS() { DFS(getFirstNode()); }

  /**
   * Get the first node, either entry or exit
   * depending on which way we are viewing the graph
   * @return the entry node or exit node
   */
  private BasicBlock getFirstNode() {
    if (forward) {
      return cfg.entry();
    } else {
      return cfg.exit();
    }
  }

  /**
   * Print the "next" nodes (either out or in) for the passed block
   * depending on which way we are viewing the graph
   * @param block the basic block of interest
   */
  private void printNextNodes(BasicBlock block) {
    if (forward) {
      System.out.print(block + " Succs:");
    } else {
      System.out.print(block + " Preds:");
    }
    Enumeration<BasicBlock> e = getNextNodes(block);
    while (e.hasMoreElements()) {
      System.out.print(" ");
      System.out.print(e.nextElement());
    }
    System.out.println();
  }

  /**
   * Returns an enumeration of the "next" nodes (either out or in) for the
   * passed block depending on which way we are viewing the graph
   * @param block the basic block of interest
   */
  private Enumeration<BasicBlock> getNextNodes(BasicBlock block) {
    Enumeration<BasicBlock> bbEnum;
    if (forward) {
      bbEnum = block.getOut();
    } else {
      bbEnum = block.getIn();
    }
    return bbEnum;
  }

  /**
   * Returns an enumeration of the "prev" nodes (either in or out) for the
   * passed block depending on which way we are viewing the graph
   * @param block the basic block of interest
   */
  private Enumeration<BasicBlock> getPrevNodes(BasicBlock block) {
    Enumeration<BasicBlock> bbEnum;
    if (forward) {
      bbEnum = block.getIn();
    } else {
      bbEnum = block.getOut();
    }
    return bbEnum;
  }

  /**
   * The non-recursive depth-first numbering code called from Step 1.
   * The recursive version was too costly on the toba benchmark on Linux/IA32.
   * @param block the basic block to process
   */
  protected void DFS(BasicBlock block) {

    // push node on to the emulated activation stack
    push(block);

    recurse:
    while (!empty()) {

      block = peek();
      if (DEBUG) { System.out.println(" Processing (peek)" + block); }

      if (block == null) {
        if (DEBUG) { System.out.println(" Popping"); }
        pop();   // return
        continue;
      }

      // The current Dominance Frontier and SSA code assumes the exit
      // node will not be part of the (regular) dominator solution.
      // To avoid this from happening we screen it out here for forward CFG
      //
      // However, it really shouldn't be in the CFG, if it isn't a node!
      if (forward && block == cfg.exit()) {
        if (DEBUG) { System.out.println(" Popping"); }
        pop();   // return
        continue;
      }

      Enumeration<BasicBlock> e;
      e = LTDominatorInfo.getInfo(block).getEnum();

      if (e == null) {
        if (DEBUG) { System.out.println(" Initial processing of " + block); }

        DFSCounter++;
        LTDominatorInfo.getInfo(block).setSemiDominator(DFSCounter);
        vertex[DFSCounter] = block;
        e = getNextNodes(block);
      } else {
        if (DEBUG) { System.out.println(" Resuming processing of " + block); }
      }

      while (e.hasMoreElements()) {
        BasicBlock next = e.nextElement();

        if (DEBUG) { System.out.println("    Inspecting next node: " + next); }

        // We treat the exit node as not being in the CFG for forward direction
        if (forward && next.isExit()) {
          continue;  // inner loop
        }
        if (getSemi(next) == 0) {
          LTDominatorInfo.getInfo(next).setParent(block);

          // simulate a recursive call
          // save the enumeration state for resumption later
          LTDominatorInfo.getInfo(block).setEnum(e);

          if (DEBUG) { System.out.println(" Pushing" + next); }
          push(next);
          continue recurse;
        }
      }           // while more nexts
      // "Pop" from the emulated activiation stack
      if (DEBUG) { System.out.println(" Popping"); }
      pop();
    }  // while stack not empty loop
  }

  /**
   *  This is the heart of the algorithm.  See sources for details.
   */
  private void step2() {
    if (DEBUG) { System.out.println(" ******* Beginning STEP 2 *******\n"); }

    // Visit each node in reverse DFS order, except for the root, which
    // has number 1
    // for i=n downto 2
    for (int i = DFSCounter; i > 1; i--) {
      // block = vertex[i]
      BasicBlock block = vertex[i];
      LTDominatorInfo blockInfo = LTDominatorInfo.getInfo(block);

      if (DEBUG) { System.out.println(" Processing: " + block + "\n"); }

      // visit each predecessor
      Enumeration<BasicBlock> e = getPrevNodes(block);
      while (e.hasMoreElements()) {
        BasicBlock prev = e.nextElement();
        if (DEBUG) { System.out.println("    Inspecting prev: " + prev); }
        BasicBlock u = EVAL(prev);
        // if semi(u) < semi(block) then semi(block) = semi(u)
        // u may be part of infinite loop and thus, is unreachable from the exit node.
        // In this case, it will have a semi value of 0.  Thus, we screen for it here
        if (getSemi(u) != 0 && getSemi(u) < getSemi(block)) {
          blockInfo.setSemiDominator(getSemi(u));
        }
      }  // while prev

      // add "block" to bucket(vertex(semi(block)));
      LTDominatorInfo.getInfo(vertex[blockInfo.getSemiDominator()]).
          addToBucket(block);

      // LINK(parent(block), block)
      LINK(blockInfo.getParent(), block);

      // foreach block2 in bucket(parent(block)) do
      java.util.Iterator<BasicBlock> bucketEnum = LTDominatorInfo.getInfo(getParent(block)).getBucketIterator();
      while (bucketEnum.hasNext()) {
        BasicBlock block2 = bucketEnum.next();

        // u = EVAL(block2)
        BasicBlock u = EVAL(block2);

        // if semi(u) < semi(block2) then
        //    dom(block2) = u
        // else
        //    dom(block2) = parent(block)
        if (getSemi(u) < getSemi(block2)) {
          LTDominatorInfo.getInfo(block2).setDominator(u);
        } else {
          LTDominatorInfo.getInfo(block2).setDominator(getParent(block));
        }
      }         // while bucket has more elements
    }           // for DFSCounter .. 1
  }             // method

  /**
   * This method inspects the passed block and returns the following:
   * <ul>
   *   <li>block, if block is a root of a tree in the forest
   *   <li>any vertex, u != r such that r is the root of the tree
   *       containing block and semi(u) is minimum on the path  r -> v,
   *       otherwise
   * </ul>
   * <p>
   *
   * See TOPLAS 1(1), July 1979, p 128 for details.
   *
   * @param block the block to evaluate
   * @return the block as described above
   */
  private BasicBlock EVAL(BasicBlock block) {
    if (DEBUG) {
      System.out.println("  Evaling " + block);
    }
    if (getAncestor(block) == null) {
      return getLabel(block);
    } else {
      compress(block);
      if (getSemi(getLabel(getAncestor(block))) >= getSemi(getLabel(block))) {
        return getLabel(block);
      } else {
        return getLabel(getAncestor(block));
      }
    }
  }

  /**
   *  This recursive method performs the path compression
   *  @param block the block of interest
   */
  private void compress(BasicBlock block) {
    if (getAncestor(getAncestor(block)) != null) {
      compress(getAncestor(block));
      LTDominatorInfo blockInfo = LTDominatorInfo.getInfo(block);
      if (getSemi(getLabel(getAncestor(block))) < getSemi(getLabel(block))) {
        blockInfo.setLabel(getLabel(getAncestor(block)));
      }
      blockInfo.setAncestor(getAncestor(getAncestor(block)));
    }
  }

  /**
   *  Adds edge (block1, block2) to the forest maintained as an auxiliary
   *  data structure.  This implementation uses path compression and
   *  results in a O(e * alpha(e,n)) complexity, where e is the number of
   *  edges in the CFG and n is the number of nodes.
   *
   *  @param block1 a basic block corresponding to the source of the new edge
   *  @param block2 a basic block corresponding to the source of the new edge
   */
  private void LINK(BasicBlock block1, BasicBlock block2) {
    if (DEBUG) {
      System.out.println("  Linking " + block1 + " with " + block2);
    }
    BasicBlock s = block2;
    while (getSemi(getLabel(block2)) < getSemi(getLabel(getChild(s)))) {
      if (getSize(s) + getSize(getChild(getChild(s))) >= 2 * getSize(getChild(s))) {
        LTDominatorInfo.getInfo(getChild(s)).setAncestor(s);
        LTDominatorInfo.getInfo(s).setChild(getChild(getChild(s)));
      } else {
        LTDominatorInfo.getInfo(getChild(s)).setSize(getSize(s));
        LTDominatorInfo.getInfo(s).setAncestor(getChild(s));
        s = getChild(s);
      }
    }
    LTDominatorInfo.getInfo(s).setLabel(getLabel(block2));
    LTDominatorInfo.getInfo(block1).setSize(getSize(block1) + getSize(block2));
    if (getSize(block1) < 2 * getSize(block2)) {
      BasicBlock tmp = s;
      s = getChild(block1);
      LTDominatorInfo.getInfo(block1).setChild(tmp);
    }
    while (s != null) {
      LTDominatorInfo.getInfo(s).setAncestor(block1);
      s = getChild(s);
    }
    if (DEBUG) {
      System.out.println("  .... done");
    }
  }

  /**
   *  This final step sets the final dominator information.
   */
  private void step3() {
    // Visit each node in DFS order, except for the root, which has number 1
    for (int i = 2; i <= DFSCounter; i++) {
      BasicBlock block = vertex[i];
      // if dom(block) != vertex[semi(block)]
      if (getDom(block) != vertex[getSemi(block)]) {
        // dom(block) = dom(dom(block))
        LTDominatorInfo.getInfo(block).setDominator(getDom(getDom(block)));
      }
    }
  }

  //
  // The following methods are simple helper methods to increase the
  // readability of the code.
  //

  /**
   * Returns the current dominator for the passed block
   * @param block
   * @return the domiator for the passed block
   */
  private BasicBlock getDom(BasicBlock block) {
    return LTDominatorInfo.getInfo(block).getDominator();
  }

  /**
   * Returns the parent for the passed block
   * @param block
   * @return the parent for the passed block
   */
  private BasicBlock getParent(BasicBlock block) {
    return LTDominatorInfo.getInfo(block).getParent();
  }

  /**
   * Returns the ancestor for the passed block
   * @param block
   * @return the ancestor for the passed block
   */
  private BasicBlock getAncestor(BasicBlock block) {
    return LTDominatorInfo.getInfo(block).getAncestor();
  }

  /**
   * returns the label for the passed block or null if the block is null
   * @param block
   * @return the label for the passed block or null if the block is null
   */
  private BasicBlock getLabel(BasicBlock block) {
    if (block == null) {
      return null;
    }
    return LTDominatorInfo.getInfo(block).getLabel();
  }

  /**
   * Returns the current semidominator for the passed block
   * @param block
   * @return the semidominator for the passed block
   */
  private int getSemi(BasicBlock block) {
    if (block == null) {
      return 0;
    }
    return LTDominatorInfo.getInfo(block).getSemiDominator();
  }

  /**
   * returns the size associated with the block
   * @param block
   * @return the size of the block or 0 if the block is null
   */
  private int getSize(BasicBlock block) {
    if (block == null) {
      return 0;
    }
    return LTDominatorInfo.getInfo(block).getSize();
  }

  /**
   * Get the child node for this block
   * @param block
   * @return the child node
   */
  private BasicBlock getChild(BasicBlock block) {
    return LTDominatorInfo.getInfo(block).getChild();
  }

  /**
   * Print the nodes that dominate each basic block
   * @param ir the IR
   */
  private void printResults(IR ir) {
    if (forward) {
      System.out.println("Results of dominators computation for method " + ir.method.getName() + "\n");
      System.out.println("   Here's the CFG:");
      System.out.println(ir.cfg);
      System.out.println("\n\n  Here's the Dominator Info:");
    } else {
      System.out.println("Results of Post-Dominators computation for method " + ir.method.getName() + "\n");
      System.out.println("   Here's the CFG:");
      System.out.println(ir.cfg);
      System.out.println("\n\n  Here's the Post-Dominator Info:");
    }

    for (Enumeration<BasicBlock> bbEnum = cfg.basicBlocks(); bbEnum.hasMoreElements();) {
      BasicBlock block = bbEnum.nextElement();
      // We don't compute a result for the exit node for forward direction
      if (!forward || !block.isExit()) {
        System.out.println("Dominators of " + block + ":" + LTDominatorInfo.getInfo(block).dominators(block, ir));
      }
    }
    System.out.println("\n");
  }

  /**
   *  Print the result of the DFS numbering performed in Step 1
   */
  private void printDFSNumbers() {
    for (Enumeration<BasicBlock> bbEnum = cfg.basicBlocks(); bbEnum.hasMoreElements();) {
      BasicBlock block = bbEnum.nextElement();
      // We don't compute a result for the exit node for forward direction
      if (forward && block.isExit()) {
        continue;
      }
      LTDominatorInfo info = (LTDominatorInfo) block.scratchObject;
      System.out.println(" " + block + " " + info);
    }
    // Visit each node in reverse DFS order, except for the root, which
    // has number 1
    for (int i = 1; i <= DFSCounter; i++) {
      System.out.println(" Vertex: " + i + " " + vertex[i]);
    }
  }
}
