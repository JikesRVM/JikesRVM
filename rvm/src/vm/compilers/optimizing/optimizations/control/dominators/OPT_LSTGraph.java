/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.util.*;

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
 * @author Mauricio J. Serrano
 * @modified Stephen Fink
 * @modified Dave Grove
 * @modified Michael Hind
 */
public class OPT_LSTGraph extends OPT_SpaceEffGraph {
  private static final boolean DEBUG = false;

  private OPT_LSTNode rootNode;
  private final java.util.HashMap loopMap = new java.util.HashMap(); // bb -> OPT_LSTNode of innermost loop containing bb

  /**
   * The main entry point
   * @param ir the IR to process
   */
  public static void perform(OPT_IR ir) {
    if (DEBUG) System.out.println("LSTGraph:" + ir.method);
    ir.HIRInfo.LoopStructureTree = new OPT_LSTGraph(ir);
    if (DEBUG) {
      OPT_VCG.printVCG("cfg", ir.cfg);
      OPT_VCG.printVCG("lst", ir.HIRInfo.LoopStructureTree);
      System.out.println(ir.HIRInfo.LoopStructureTree.toString());
    }
    
  }

  /**
   * @param bb the basic block
   * @return the loop nesting depth or 0, if not in loop
   */
  public int getLoopNestDepth(OPT_BasicBlock bb) {
    OPT_LSTNode loop = (OPT_LSTNode)loopMap.get(bb);
    if (loop == null) return 0;
    return loop.depth;
  }

  /**
   * Is a given basic block in an innermost loop?
   * @param bb the basic block
   * @return whether the block is in an innermost loop
   */
  public boolean inInnermostLoop(OPT_BasicBlock bb) {
    OPT_LSTNode node = (OPT_LSTNode)loopMap.get(bb);
    if (node == null) return false;
    if (node.firstOutEdge() == null && node.loop != null) { 
      return true;
    } else {
      return false;
    }
  }

  /**
   * Is the edge from source to target an exit from the loop containing source?
   * @param source the basic block that is the source of the edge
   * @param target the basic block that is the target of the edge
   */
  public boolean isLoopExit(OPT_BasicBlock source, OPT_BasicBlock target) {
    OPT_LSTNode snode = (OPT_LSTNode)loopMap.get(source);
    OPT_LSTNode tnode = (OPT_LSTNode)loopMap.get(target);

    if (snode == null || snode == rootNode) return false; // source isn't in a loop
    if (tnode == null || tnode == rootNode) return true;  // source is in a loop and target isn't
    if (snode == tnode) return false; // in same loop
    
    for (OPT_LSTNode ptr = tnode; 
         ptr != rootNode; 
         ptr = ptr.getParent()) {
      if (ptr == snode) return false; // tnode is nested inside of snode
    }

    return true;
  }

  public OPT_LSTNode getLoop(OPT_BasicBlock b) {
    return (OPT_LSTNode)loopMap.get(b);
  }

  /**
   * Return the root node of the tree
   * @return the root node of the loop structure tree
   */
  public OPT_LSTNode getRoot() {
    return rootNode;
  }

  public String toString() {
    return "LST:\n" + dumpIt(rootNode);
  }

  private String dumpIt(OPT_LSTNode n) {
    String ans = n.toString() + "\n";
    for (Enumeration e = n.getChildren();
         e.hasMoreElements();) {
      ans += dumpIt((OPT_LSTNode)e.nextElement());
    }
    return ans;
  }


  /*
   * Code to construct the LST for an IR.
   */

  /**
   * Constructor, it creates the LST graph
   * @param  ir the IR
   */
  private OPT_LSTGraph(OPT_IR ir) {
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

    // Set loop nest depth for each node in the LST and initialize LoopMap
    ir.resetBasicBlockMap();
    setDepth(ir, rootNode, 0);
  }

  private void setDepth(OPT_IR ir, OPT_LSTNode node, int depth) {
    if (VM.VerifyAssertions) VM._assert(node.depth == 0);
    node.depth = depth;
    for (Enumeration e = node.getChildren();
         e.hasMoreElements();) {
      setDepth(ir, (OPT_LSTNode)e.nextElement(), depth+1);
    }
    OPT_BitVector loop = node.loop;
    if (loop != null) {
      for (int i = 0; i < loop.length(); i++) {
        if (loop.get(i)) {
          OPT_BasicBlock bb = ir.getBasicBlock(i);
          if (loopMap.get(bb) == null) {
            loopMap.put(bb, node);
          }
        }
      }
    }
  }

  /**
   * This routine performs a non-recursive depth-first search starting at
   *  the block passed looking for back edges.  It uses dominator information
   *  to determine back edges.
   * @param bb the basic block to process
   * @param numNodes the number of basic block
   */
  private void findBackEdges(OPT_BasicBlock bb, int numBlocks) {
    OPT_Stack stack = new OPT_Stack();
    OPT_SpaceEffGraphNode.GraphEdgeEnumeration[] BBenum = 
      new OPT_SpaceEffGraphNode.GraphEdgeEnumeration[numBlocks];
    
    // push node on to the emulated activation stack
    stack.push(bb);

  recurse:
    while (!stack.empty()) {
      bb = (OPT_BasicBlock) stack.peek();

      // check if we were already processing this node, if so we would have
      // saved the state of the enumeration in the loop below
      OPT_SpaceEffGraphNode.GraphEdgeEnumeration e = BBenum[bb.getNumber()];
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
            throw new OPT_OptimizingCompilerException("irreducible loop found!");
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
    OPT_SpaceEffGraphNode.GraphEdgeEnumeration f = bb.outEdges();
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
  private void findNaturalLoop(OPT_SpaceEffGraphEdge edge, OPT_BitVector loop) {

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
