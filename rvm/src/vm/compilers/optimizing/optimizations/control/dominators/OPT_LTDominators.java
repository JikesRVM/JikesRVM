/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import  java.util.*;

/**
 * Calculate dominators using Langauer and Tarjan's fastest algorithm.
 * TOPLAS 1(1), July 1979.  This implementation uses path compression and
 * results in a O(e * alpha(e,n)) complexity, where e is the number of
 * edges in the CFG and n is the number of nodes.
 *  
 * Sources: TOPLAS article, Muchnick book
 *
 * The current implementation (4/25/00) does not include the EXIT node
 * in any solution despite the fact that it is part of the CFG (it has
 * incoming edges).  This is to be compatible with the old code.
 * 
 * @author Michael Hind
 */
class OPT_LTDominators extends OPT_Stack {
  static final boolean DEBUG = false;

  /*
   * Indicates whether we perform the algorithm over the CFG or 
   *  the reverse CFG, i.e., whether we are computing dominators or
   *  post-dominators.
   */
  private boolean forward;

  /*
   * a counter for assigning DFS numbers
   */
  protected int DFSCounter;

  /*
   * a mapping from DFS number to their basic blocks
   */
  private OPT_BasicBlock[] vertex;

  /*
   * a convenient place to locate the cfg to avoid passing it internally
   */
  private OPT_ControlFlowGraph cfg;

  /**
   * The entry point for this phase
   * @param ir the IR
   * @param forward Should we compute regular dominators, or post-dominators?
   * @param unfactor Should we unfactor the CFG?
   */
  public static void perform(OPT_IR ir, boolean forward, boolean unfactor) {
    if (ir.hasReachableExceptionHandlers()) {
      if (unfactor) {
        ir.unfactor(); 
      } else {
        throw  new OPT_OperationNotImplementedException(
            "IR with exception handlers");
      }
    }
    OPT_LTDominators dom = new OPT_LTDominators(ir, forward);
    dom.analyze(ir);
  }

  /**
   * Compute approximate dominator/post dominator without unfactoring 
   * exception handlers.  Can only be used if what the client wants is
   * approximate domination (ie, if it doesn't actually have to be correct...)
   * @param ir the IR
   * @param forward Should we compute regular dominators, or post-dominators?
   */
  public static void approximate(OPT_IR ir, boolean forward) {
    OPT_LTDominators dom = new OPT_LTDominators(ir, forward);
    dom.analyze(ir);
  }
  

  /**
   * The constructor, called by the perform method
   * @param   OPT_IR ir
   * @param forward Should we compute regular dominators, or post-dominators?
   */
  OPT_LTDominators(OPT_IR ir, boolean forward) {
    cfg = ir.cfg;               // save the cfg for easy access
    this.forward = forward;     // save the forward flag
  }

  /**
   * analyze dominators
   */
  protected void analyze(OPT_IR ir)
  {
    if (DEBUG) { 
      System.out.println("   Here's the CFG for method: "+ ir.method.getName()
                         +"\n"+ ir.cfg);
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
    cfg = null;                 // no longer need a pointer to the cfg
  }


  /**
   * Check to make sure all nodes were reached
   */
  private void checkReachability (OPT_IR ir)
  {
    if (!forward) {
      if (DFSCounter != cfg.numberOfNodes()) {
          VM.sysWrite(" *** Warning ***\n CFG for method "+ ir.method.getName()
                      + " in class " + ir.method.getDeclaringClass()
                      + " has unreachable nodes.\n");
        VM.sysWrite(" Assuming pessimistic results in dominators computation\n"
                    + " for unreachable nodes.\n");
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
    vertex = new OPT_BasicBlock[cfg.numberOfNodes() + 1];
    DFSCounter = 0;
    if (DEBUG) { System.out.println("Initializing blocks:"); }

    // Initialize each block with an empty set of predecessors and
    // a 0 for a semidominator
    for (Enumeration enum = cfg.nodes(); enum.hasMoreElements();) {
      OPT_BasicBlock block = (OPT_BasicBlock)enum.nextElement();
      // We don't compute a result for the exit node in the forward direction
      if (!forward || !block.isExit()) {
        block.scratchObject = new OPT_LTDominatorInfo(block);
        if (DEBUG) {
          printNextNodes(block);
        }
      }
    }

    DFS ();
    
    if (DEBUG) { 
      System.out.println("DFSCounter: "+ DFSCounter +", CFG Nodes: "+
                         cfg.numberOfNodes()); 
      printDFSNumbers();
    }
  }

  private void DFS () { DFS(getFirstNode()); }

  /**
   * Get the first node, either entry or exit
   * depending on which way we are viewing the graph
   * @return the entry node or exit node
   */
  private OPT_BasicBlock getFirstNode() {
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
  private void printNextNodes(OPT_BasicBlock block) {
    if (forward) {
      System.out.print(block + " Succs:");
    } else {
      System.out.print(block + " Preds:");
    }
    OPT_BasicBlockEnumeration e = getNextNodes(block);
    while (e.hasMoreElements()) {
      System.out.print(" ");
      System.out.print(e.next());
    }
    System.out.println();
  }

  /**
   * Returns an enumeration of the "next" nodes (either out or in) for the
   * passed block depending on which way we are viewing the graph
   * @param block the basic block of interest
   */
  private OPT_BasicBlockEnumeration getNextNodes(OPT_BasicBlock block) {
    OPT_BasicBlockEnumeration enum;
    if (forward) {
      enum = block.getOut();
    } else {
      enum = block.getIn();
    }
    return enum;
  }

  /**
   * Returns an enumeration of the "prev" nodes (either in or out) for the
   * passed block depending on which way we are viewing the graph
   * @param block the basic block of interest
   */
  private OPT_BasicBlockEnumeration getPrevNodes(OPT_BasicBlock block) {
    OPT_BasicBlockEnumeration enum;
    if (forward) {
      enum = block.getIn();
    } else {
      enum = block.getOut();
    }
    return enum;
  }

  /**
   * The non-recursive depth-first numbering code called from Step 1.
   * The recursive version was too costly on the toba benchmark on Linux/IA32.
   * @param block the basic block to process
   */
  protected void DFS(OPT_BasicBlock block) {

    // push node on to the emulated activation stack
    push(block);

  recurse:
    while (!empty()) {

      block = (OPT_BasicBlock) peek();
      if (DEBUG) { System.out.println(" Processing (peek)" + block);   }

      if (block == null) {
        if (DEBUG) { System.out.println(" Popping");   }
        pop();   // return
        continue recurse;  
      }

      // The current Dominance Frontier and SSA code assumes the exit
      // node will not be part of the (regular) dominator solution.
      // To avoid this from happening we screen it out here for forward CFG
      //
      // However, it really shouldn't be in the CFG, if it isn't a node!
      if (forward && block == cfg.exit()) {
        if (DEBUG) { System.out.println(" Popping");   }
        pop();   // return
        continue recurse;  
      }

      OPT_BasicBlockEnumeration e;
      e = OPT_LTDominatorInfo.getInfo(block).getEnum();

      if (e == null) {
        if (DEBUG) { System.out.println(" Initial processing of " + block);   }

        DFSCounter++;
        OPT_LTDominatorInfo.getInfo(block).setSemiDominator(DFSCounter);
        vertex[DFSCounter] = block;
        e = getNextNodes(block);
      } else {
        if (DEBUG) { System.out.println(" Resuming processing of " + block);   }
      }

      while (e.hasMoreElements()) {
        OPT_BasicBlock next = e.next();

        if (DEBUG) { System.out.println("    Inspecting next node: " + next); }

        // We treat the exit node as not being in the CFG for forward direction
        if (forward && next.isExit()) {
          continue;  // inner loop
        }
        if (getSemi(next) == 0) {
          OPT_LTDominatorInfo.getInfo(next).setParent(block);

          // simulate a recursive call
          // save the enumeration state for resumption later
          OPT_LTDominatorInfo.getInfo(block).setEnum(e);

          if (DEBUG) { System.out.println(" Pushing"+ next);   }
          push(next);
          continue recurse;  
        }
      }           // while more nexts
      // "Pop" from the emulated activiation stack
      if (DEBUG) { System.out.println(" Popping");   }
      pop();
    }  // while stack not empty loop
  }  

  /**
   *  This is the heart of the algorithm.  See sources for details.
   */
  private void step2() {
    if (DEBUG) { System.out.println(" ******* Beginning STEP 2 *******\n");  }

    // Visit each node in reverse DFS order, except for the root, which
    // has number 1
    // for i=n downto 2
    for (int i = DFSCounter; i > 1; i--) {
      // block = vertex[i]
      OPT_BasicBlock block = vertex[i];
      OPT_LTDominatorInfo blockInfo = OPT_LTDominatorInfo.getInfo(block);

      if (DEBUG) { System.out.println(" Processing: "+ block +"\n"); }

      // visit each predecessor
      OPT_BasicBlockEnumeration e = getPrevNodes(block);
      while (e.hasMoreElements()) {
        OPT_BasicBlock prev = e.next();
        if (DEBUG) {  System.out.println("    Inspecting prev: " + prev);   }
        OPT_BasicBlock u = EVAL(prev);
        // if semi(u) < semi(block) then semi(block) = semi(u)
        // u may be part of infinite loop and thus, is unreachable from the exit node.
        // In this case, it will have a semi value of 0.  Thus, we screen for it here
        if (getSemi(u) != 0 && getSemi(u) < getSemi(block)) {
          blockInfo.setSemiDominator(getSemi(u));
        }
      }  // while prev

      // add "block" to bucket(vertex(semi(block)));
      OPT_LTDominatorInfo.getInfo(vertex[blockInfo.getSemiDominator()]).
                                                            addToBucket(block);

      // LINK(parent(block), block)
      LINK(blockInfo.getParent(), block);

      // foreach block2 in bucket(parent(block)) do
      java.util.Iterator bucketEnum = 
        OPT_LTDominatorInfo.getInfo(getParent(block)).getBucketIterator();
      while (bucketEnum.hasNext()) {
        OPT_BasicBlock block2 = (OPT_BasicBlock)bucketEnum.next();

        // u = EVAL(block2)
        OPT_BasicBlock u = EVAL(block2);

        // if semi(u) < semi(block2) then
        //    dom(block2) = u
        // else
        //    dom(block2) = parent(block)
        if (getSemi(u) < getSemi(block2)) {
          OPT_LTDominatorInfo.getInfo(block2).setDominator(u);
        } else {
          OPT_LTDominatorInfo.getInfo(block2).setDominator(getParent(block));
        }
      }         // while bucket has more elements
    }           // for DFSCounter .. 1
  }             // method

  /**
   * This method inspects the passed block and returns the following:
   *    block,                       if block is a root of a tree in the forest
   *     
   *    any vertex, u != r such that                        otherwise
   *      r is the root of the tree containing block and
   *                                        *
   *      semi(u) is minimum on the path  r -> v 
   *
   * See TOPLAS 1(1), July 1979, p 128 for details.
   *
   * @param block the block to evaluate
   * @return the block as described above
   */
  private OPT_BasicBlock EVAL(OPT_BasicBlock block) {
    if (DEBUG) {
      System.out.println("  Evaling " + block);
    }
    if (getAncestor(block) == null) {
      return  getLabel(block);
    } else {
      compress(block);
      if (getSemi(getLabel(getAncestor(block))) >= getSemi(getLabel(block))) {
        return  getLabel(block);
      } else {
        return  getLabel(getAncestor(block));
      }
    }
  }

  /**
   *  This recursive method performs the path compression
   *  @param the block of interest
   */
  private void compress(OPT_BasicBlock block) {
    if (getAncestor(getAncestor(block)) != null) {
      compress(getAncestor(block));
      OPT_LTDominatorInfo blockInfo = OPT_LTDominatorInfo.getInfo(block);
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
  private void LINK(OPT_BasicBlock block1, OPT_BasicBlock block2) {
    if (DEBUG) {
      System.out.println("  Linking " + block1 + " with " + block2);
    }
    OPT_BasicBlock s = block2;
    while (getSemi(getLabel(block2)) < getSemi(getLabel(getChild(s)))) {
      if (getSize(s) + getSize(getChild(getChild(s))) 
                                         >= 2*getSize(getChild(s))) {
        OPT_LTDominatorInfo.getInfo(getChild(s)).setAncestor(s);
        OPT_LTDominatorInfo.getInfo(s).setChild(getChild(getChild(s)));
      } else {
        OPT_LTDominatorInfo.getInfo(getChild(s)).setSize(getSize(s));
        OPT_LTDominatorInfo.getInfo(s).setAncestor(getChild(s));
        s = getChild(s);
      }
    }
    OPT_LTDominatorInfo.getInfo(s).setLabel(getLabel(block2));
    OPT_LTDominatorInfo.getInfo(block1).setSize(getSize(block1) 
        + getSize(block2));
    if (getSize(block1) < 2 * getSize(block2)) {
      OPT_BasicBlock tmp = s;
      s = getChild(block1);
      OPT_LTDominatorInfo.getInfo(block1).setChild(tmp);
    }
    while (s != null) {
      OPT_LTDominatorInfo.getInfo(s).setAncestor(block1);
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
      OPT_BasicBlock block = vertex[i];
      // if dom(block) != vertex[semi(block)]
      if (getDom(block) != vertex[getSemi(block)]) {
        // dom(block) = dom(dom(block))
        OPT_LTDominatorInfo.getInfo(block).setDominator(getDom(getDom(block)));
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
  private OPT_BasicBlock getDom(OPT_BasicBlock block) {
    return  OPT_LTDominatorInfo.getInfo(block).getDominator();
  }

  /**
   * Returns the parent for the passed block 
   * @param block 
   * @return the parent for the passed block 
   */
  private OPT_BasicBlock getParent(OPT_BasicBlock block) {
    return  OPT_LTDominatorInfo.getInfo(block).getParent();
  }

  /**
   * Returns the ancestor for the passed block 
   * @param block
   * @return the ancestor for the passed block 
   */
  private OPT_BasicBlock getAncestor(OPT_BasicBlock block) {
    return  OPT_LTDominatorInfo.getInfo(block).getAncestor();
  }

  /**
   * returns the label for the passed block or null if the block is null
   * @param block
   * @return the label for the passed block or null if the block is null
   */
  private OPT_BasicBlock getLabel(OPT_BasicBlock block) {
    if (block == null) {
      return  null;
    }
    return  OPT_LTDominatorInfo.getInfo(block).getLabel();
  }

  /**
   * Returns the current semidominator for the passed block
   * @param block
   * @return the semidominator for the passed block
   */
  private int getSemi(OPT_BasicBlock block) {
    if (block == null) {
      return  0;
    }
    return  OPT_LTDominatorInfo.getInfo(block).getSemiDominator();
  }

  /**
   * returns the size associated with the block
   * @param block
   * @return the size of the block or 0 if the block is null
   */
  private int getSize(OPT_BasicBlock block) {
    if (block == null) {
      return  0;
    }
    return  OPT_LTDominatorInfo.getInfo(block).getSize();
  }

  /**
   * Get the child node for this block
   * @param block
   * @return the child node
   */
  private OPT_BasicBlock getChild(OPT_BasicBlock block) {
    return  OPT_LTDominatorInfo.getInfo(block).getChild();
  }

  /**
   * Print the nodes that dominate each basic block
   * @param ir the IR
   */
  private void printResults(OPT_IR ir) {
    if (forward) {
      System.out.println("Results of dominators computation for method "+
                         ir.method.getName() +"\n");
      System.out.println("   Here's the CFG:");
      System.out.println(ir.cfg);
      System.out.println("\n\n  Here's the Dominator Info:");
    } else {
      System.out.println("Results of Post-Dominators computation for method "+
                         ir.method.getName() +"\n");
      System.out.println("   Here's the CFG:");
      System.out.println(ir.cfg);
      System.out.println("\n\n  Here's the Post-Dominator Info:");
    }
    
    for (Enumeration enum = cfg.nodes(); enum.hasMoreElements();) {
      OPT_BasicBlock block = (OPT_BasicBlock)enum.nextElement();
      // We don't compute a result for the exit node for forward direction
      if (!forward || !block.isExit()) {
        System.out.println("Dominators of " + block + ":" 
            + OPT_LTDominatorInfo.getInfo(block).dominators(block,ir));
      }
    }
    System.out.println("\n");
  }

  /**
   *  Print the result of the DFS numbering performed in Step 1
   */
  private void printDFSNumbers() {
    for (Enumeration enum = cfg.nodes(); enum.hasMoreElements();) {
      OPT_BasicBlock block = (OPT_BasicBlock)enum.nextElement();
      // We don't compute a result for the exit node for forward direction
      if (forward && block.isExit()) {
        continue;
      }
      OPT_LTDominatorInfo info = (OPT_LTDominatorInfo)block.scratchObject;
      System.out.println(" "+ block +" "+ info);
    }
    // Visit each node in reverse DFS order, except for the root, which
    // has number 1
    for (int i = 1; i <= DFSCounter; i++) {
      System.out.println(" Vertex: " + i + " " + vertex[i]);
    }
  }
}



