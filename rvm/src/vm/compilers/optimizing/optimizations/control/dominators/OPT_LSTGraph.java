/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.*;
import  OPT_SpaceEffGraphNode.*;

/**
 * Identify natural loops and builds the LST (Loop Structure Tree)
 *
 * Note: throws an exception if an irreducible loop is found 
 * (which I believe could only happen in Java from modified bytecode, 
 *  because Java source code is structured enough to prevent 
 *  irreducible loops.)
 *
 * @see OPT_DominatorsPhase
 *
 * @author: Mauricio J. Serrano
 * @modified: Stephen Fink
 * @modified: Michael Hind
 */
public class OPT_LSTGraph extends OPT_SpaceEffGraph {
  static final boolean DEBUG = false;

  /** Implementation */
  private java.util.HashMap hash = new java.util.HashMap();   // bb -> node
  private OPT_LSTNode rootNode;
  private java.util.HashMap nest;            // bb -> Integer (nesting depth)
  private OPT_IR ir;

  /**
   * The main entry point
   * @param ir the IR to process
   */
  public static void perform(OPT_IR ir) {
    if (DEBUG) {   System.out.println("LSTGraph:" + ir.method); }
    ir.HIRInfo.LoopStructureTree = new OPT_LSTGraph(ir);
    if (DEBUG) {
      OPT_VCG.printVCG("cfg", ir.cfg);
      OPT_VCG.printVCG("lst", ir.HIRInfo.LoopStructureTree);
    }
  }

  /**
   * Constructor, it creates the LST graph
   * @param  ir the IR
   */
  OPT_LSTGraph(OPT_IR ir) {
    this.ir = ir;
    OPT_ControlFlowGraph cfg = ir.cfg;
    OPT_BasicBlock entry = ir.cfg.entry();

    // do DFN pass
    cfg.clearDFS();
    entry.sortDFS();
    int dfn = 0;
    for (OPT_SpaceEffGraphNode node = entry; 
	 node != null; 
	 node = node.nextSorted) {
      node.clearLoopHeader();
      node.scratch = dfn++;
      clearBackEdges(node);
    }
    cfg.clearDFS();
    findBackEdges(entry, ir.cfg.numberOfNodes());

    // entry node is considered the LST head
    OPT_LSTNode lstheader = new OPT_LSTNode(entry);
    rootNode = lstheader;
    addGraphNode(lstheader);
    hash.put(entry, lstheader);

    // compute the natural loops for each back edge. 
    // merge backedges with the same header
    for (OPT_BasicBlock node = (OPT_BasicBlock)entry.nextSorted; 
	 node != null;
	 node = (OPT_BasicBlock)node.nextSorted) {
      OPT_LSTNode header = null;
      for (OPT_SpaceEffGraphEdge edge = node.firstInEdge(); 
	   edge != null; 
	   edge = edge.getNextIn()) {
        if (edge.backEdge()) {
          OPT_BitVector loop;
          if (header == null) {
            header = new OPT_LSTNode(node);
            addGraphNode(header);
            hash.put(node, header);
            loop = new OPT_BitVector(cfg.numberOfNodes());
            loop.set(node.getNumber());
            header.loop = loop;
            if (DEBUG) {  System.out.println("header" + header); }
          } else {
            loop = header.loop;
	  }
          cfg.clearDFS();
          node.setDfsVisited();
          findNaturalLoop(edge, loop);
        }
      }
    }
    if (DEBUG) {
      for (OPT_SpaceEffGraphNode node = _firstNode; 
	   node != null; 
	   node = node.getNext()) {
        System.out.println(node);
      }
    }

    // now build the LST
  lstloop: 
    for (OPT_LSTNode node = (OPT_LSTNode)_firstNode.getNext(); 
		node != null; 
		node = (OPT_LSTNode)node.getNext()) {
      int number = node.header.getNumber();
      for (OPT_LSTNode prev = (OPT_LSTNode)node.getPrev(); 
	   prev != _firstNode; 
	   prev = (OPT_LSTNode)prev.getPrev()) {
	if (prev.loop.get(number)) {            // nested
	  prev.insertOut(node);
	  continue  lstloop;
	}
      }
      // else the node is considered to be connected to the LST head
      _firstNode.insertOut(node);
    }
  }

  /**
   * return the loop nesting depth of a basic block.
   * returns 0 if not in a loop
   * @param bb the basic block
   * @return the loop nesting depth or 0, if not in loop
   */
  public int getLoopNestDepth(OPT_BasicBlock bb) {
    if (nest == null)
      initializeNesting();
    Integer depth = (Integer)nest.get(bb);
    if (depth == null) {
      return 0;
    }
    return depth.intValue();
  }

  /**
   * Is a given basic block in an innermost loop?
   * @param bb the basic block
   * @return whether the block is in an innermost loop
   */
  public boolean inInnermostLoop(OPT_BasicBlock bb) {
    OPT_LSTNode node = (OPT_LSTNode)hash.get(bb);
    if (VM.VerifyAssertions) VM.assert(node != null);
    if (node.firstOutEdge() == null && node.loop != null) { 
      return true;
    } else {
      return false;
    }
  }


  /**
   * Initialize a structure which holds the loop nest depth
   * for every basic block in loops in the IR
   * TODO: This data structure is inefficient.  Re-design this whole
   *  	shebang.
   */
  private void initializeNesting() {
    nest = new java.util.HashMap();
    ir.resetBasicBlockMap();
    // for each node in the LST ...
    for (OPT_LSTNode node = (OPT_LSTNode)_firstNode.getNext(); 
	 node != null; 
	 node = (OPT_LSTNode)node.getNext()) {
      int depth = computeDepth(node);
      OPT_BitVector loop = node.loop;

      // set the nesting depth for each basic block in the loop
      for (int i = 0; i < loop.length(); i++) {
        if (loop.get(i)) {
          OPT_BasicBlock bb = ir.getBasicBlock(i);
          Integer d = (Integer)nest.get(bb);
          if (d == null) {
            nest.put(bb, new Integer(depth));
          } 
          else {
            // only set the depth deeper than before; not shallower
            int newDepth = Math.max(depth, d.intValue());
            nest.put(bb, new Integer(newDepth));
          }
        }
      }
    }
  }

  /**
   * How many levels of nesting enclose a particular LSTNode?
   * @param n the LSTnode of interest
   * @return the number levels of nesting enclosing n
   */
  private int computeDepth(OPT_LSTNode n) {
    int depth = 0;
    while (n != rootNode) {
      depth++;
      if (n.getNumberOfIn() != 1) {
        throw  new OPT_OptimizingCompilerException("Invalid LST");
      }
      n = (OPT_LSTNode)n.inNodes().next();
    }
    return depth;
  }

  /**
   * Return the root node of the tree
   * @return the root node of the loop structure tree
   */
  public OPT_LSTNode getRoot() {
    return rootNode;
  }

  /**
   * This routine performs a non-recursive depth-first search starting at
   *  the block passed looking for back edges.  It uses dominator information
   *  to determine back edges.
   * @param bb the basic block to process
   * @param numNodes the number of basic block
   */
  static void findBackEdges(OPT_BasicBlock bb, int numBlocks) {
    OPT_Stack stack = new OPT_Stack();
    GraphEdgeEnumeration[] BBenum = new GraphEdgeEnumeration[numBlocks];

    // push node on to the emulated activation stack
    stack.push(bb);

  recurse:
    while (!stack.empty()) {
      bb = (OPT_BasicBlock) stack.peek();

      // check if we were already processing this node, if so we would have
      // saved the state of the enumeration in the loop below
      GraphEdgeEnumeration e = BBenum[bb.getNumber()];
      if (e == null) {
	if (DEBUG) { System.out.println(" Initial processing of " + bb);  }
	bb.setDfsVisited();
	e = bb.outEdges();
      } else {
	if (DEBUG) { System.out.println(" Resuming processing of " + bb); }
      }

      while (e.hasMoreElements()) {
	OPT_SpaceEffGraphEdge outEdge = e.next();

	OPT_BasicBlock outbb = (OPT_BasicBlock)outEdge.toNode();
	if (OPT_LTDominatorInfo.isDominatedBy(bb, outbb)) {   // backedge
	  outbb.setLoopHeader();
	  outEdge.setBackEdge();
	  if (DEBUG) {
	    System.out.println("backedge from " + bb.scratch + " ( " + bb
			       + " ) " + outbb.scratch + " ( " 
			       + outbb + " ) ");
	  }
	} else if (!outbb.dfsVisited()) {
	  // irreducible loop test
	  if (outbb.scratch < bb.scratch)
	    throw  new OPT_OptimizingCompilerException("irreducible loop found!");
	  // simulate a recursive call
	  // but first save the enumeration state for resumption later
	  BBenum[bb.getNumber()] = e;
	  stack.push(outbb);
	  continue recurse;
	}
      } // enum
      // "Pop" from the emulated activiation stack
      if (DEBUG) { System.out.println(" Popping");   }
      stack.pop();
    } // while !empty
  }

  /**
   * Clears the back edges for the basic block passed
   * @param bb the basic block
   */
  private void clearBackEdges(OPT_SpaceEffGraphNode bb) {
    GraphEdgeEnumeration f = bb.outEdges();
    while (f.hasMoreElements()) {
      OPT_SpaceEffGraphEdge outEdge = f.next();
      outEdge.clearBackEdge();
    }
  }

  /**
   * This routine implements part of the algorithm to compute natural loops 
   *  as defined in Muchnick p 192.  See caller for more details.
   * @param edge the edge to process
   * @param loop bit vector to hold the results of the algorithm
   */
  static void findNaturalLoop(OPT_SpaceEffGraphEdge edge, 
			      OPT_BitVector loop) {

    /* Algorithm to compute Natural Loops, Muchnick, pp. 192: 
       procedure Nat_Loop(m,n,Pred) return set of Node
       m, n: in Node
       Pred: in Node -> set of Node
       begin
       Loop:  set of Node
       Stack: sequence of Node
       p, q: Node
       Stack := []
       Loop  := {m,n}
       if m != n then
       Stack += [m]
       while Stack != [] do
       // add predecessors of m that are not predecessors of n
       // to the set of nodes in the loop; since n dominates m,
       // this only adds nodes in the loop
       p := Stack drop -1
       Stack -= -1
       for each q in Pred(p) do
       if q belongs Loop then
       Loop U= {q}
       Stack += [q]
       
       return Loop
       end
    */

    OPT_SpaceEffGraphNode fromNode = edge.fromNode();
    if (!fromNode.dfsVisited()) {
      fromNode.setDfsVisited();
      loop.set(fromNode.getNumber());
      for (OPT_SpaceEffGraphEdge in = fromNode.firstInEdge(); 
	   in != null; 
	   in = in.getNextIn()) {
        findNaturalLoop(in, loop);
      }
    }
  }
}



