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
import java.util.HashMap;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.OptimizingCompilerException;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.ControlFlowGraph;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.util.SpaceEffGraph;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphEdge;
import org.jikesrvm.compilers.opt.util.SpaceEffGraphNode;
import org.jikesrvm.compilers.opt.util.Stack;
import org.jikesrvm.util.BitVector;

/**
 * Identify natural loops and builds the LST (Loop Structure Tree)
 *
 * Note: throws an exception if an irreducible loop is found
 * (which I believe could only happen in Java from modified bytecode,
 *  because Java source code is structured enough to prevent
 *  irreducible loops.)
 *
 * @see DominatorsPhase
 */
public class LSTGraph extends SpaceEffGraph {
  private static final boolean DEBUG = false;

  protected LSTNode rootNode;
  /** Map of bb to LSTNode of innermost loop containing bb */
  private final HashMap<BasicBlock, LSTNode> loopMap;

  /**
   * The main entry point
   * @param ir the IR to process
   */
  public static void perform(IR ir) {
    if (DEBUG) System.out.println("LSTGraph:" + ir.method);
    ir.HIRInfo.loopStructureTree = new LSTGraph(ir);
    if (DEBUG) {
      System.out.println(ir.HIRInfo.loopStructureTree.toString());
    }
  }

  /**
   * @param bb the basic block
   * @return the loop nesting depth or 0, if not in loop
   */
  public int getLoopNestDepth(BasicBlock bb) {
    LSTNode loop = loopMap.get(bb);
    if (loop == null) return 0;
    return loop.depth;
  }

  /**
   * Is a given basic block in an innermost loop?
   * @param bb the basic block
   * @return whether the block is in an innermost loop
   */
  public boolean inInnermostLoop(BasicBlock bb) {
    LSTNode node = loopMap.get(bb);
    return node != null && node.firstOutEdge() == null && node.loop != null;
  }

  /**
   * Is the edge from source to target an exit from the loop containing source?
   * @param source the basic block that is the source of the edge
   * @param target the basic block that is the target of the edge
   */
  public boolean isLoopExit(BasicBlock source, BasicBlock target) {
    LSTNode snode = loopMap.get(source);
    LSTNode tnode = loopMap.get(target);

    if (snode == null || snode == rootNode) return false; // source isn't in a loop
    if (tnode == null || tnode == rootNode) return true;  // source is in a loop and target isn't
    if (snode == tnode) return false; // in same loop

    for (LSTNode ptr = tnode; ptr != rootNode; ptr = ptr.getParent()) {
      if (ptr == snode) return false; // tnode is nested inside of snode
    }

    return true;
  }

  public LSTNode getLoop(BasicBlock b) {
    return loopMap.get(b);
  }

  /**
   * Return the root node of the tree
   * @return the root node of the loop structure tree
   */
  public LSTNode getRoot() {
    return rootNode;
  }

  public String toString() {
    return "LST:\n" + dumpIt(rootNode);
  }

  private String dumpIt(LSTNode n) {
    String ans = n.toString() + "\n";
    for (Enumeration<LSTNode> e = n.getChildren(); e.hasMoreElements();) {
      ans += dumpIt(e.nextElement());
    }
    return ans;
  }

  /*
  * Code to construct the LST for an IR.
  */

  /**
   * Copying constructor
   *
   * @param graph to copy
   */
  protected LSTGraph(LSTGraph graph) {
    rootNode = graph.rootNode;
    loopMap = graph.loopMap;
  }

  /**
   * Constructor, it creates the LST graph
   * @param  ir the IR
   */
  private LSTGraph(IR ir) {
    loopMap = new HashMap<BasicBlock, LSTNode>();

    ControlFlowGraph cfg = ir.cfg;
    BasicBlock entry = ir.cfg.entry();

    // do DFN pass
    cfg.clearDFS();
    entry.sortDFS();
    int dfn = 0;
    for (SpaceEffGraphNode node = entry; node != null; node = node.nextSorted) {
      node.clearLoopHeader();
      node.scratch = dfn++;
      clearBackEdges(node);
    }
    cfg.clearDFS();
    findBackEdges(entry, ir.cfg.numberOfNodes());

    // entry node is considered the LST head
    LSTNode lstheader = new LSTNode(entry);
    rootNode = lstheader;
    addGraphNode(lstheader);

    // compute the natural loops for each back edge.
    // merge backedges with the same header
    for (BasicBlock node = (BasicBlock) entry.nextSorted; node != null; node = (BasicBlock) node.nextSorted)
    {
      LSTNode header = null;
      for (SpaceEffGraphEdge edge = node.firstInEdge(); edge != null; edge = edge.getNextIn()) {
        if (edge.backEdge()) {
          BitVector loop;
          if (header == null) {
            header = new LSTNode(node);
            addGraphNode(header);
            loop = new BitVector(cfg.numberOfNodes());
            loop.set(node.getNumber());
            header.loop = loop;
            if (DEBUG) { System.out.println("header" + header); }
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
      for (SpaceEffGraphNode node = _firstNode; node != null; node = node.getNext()) {
        System.out.println(node);
      }
    }

    // now build the LST
    lstloop:
    for (LSTNode node = (LSTNode) _firstNode.getNext(); node != null; node = (LSTNode) node.getNext()) {
      int number = node.header.getNumber();
      for (LSTNode prev = (LSTNode) node.getPrev(); prev != _firstNode; prev = (LSTNode) prev.getPrev()) {
        if (prev.loop.get(number)) {            // nested
          prev.insertOut(node);
          continue lstloop;
        }
      }
      // else the node is considered to be connected to the LST head
      _firstNode.insertOut(node);
    }

    // Set loop nest depth for each node in the LST and initialize LoopMap
    ir.resetBasicBlockMap();
    setDepth(ir, rootNode, 0);
  }

  private void setDepth(IR ir, LSTNode node, int depth) {
    if (VM.VerifyAssertions) VM._assert(node.depth == 0);
    node.depth = depth;
    for (Enumeration<LSTNode> e = node.getChildren(); e.hasMoreElements();) {
      setDepth(ir, e.nextElement(), depth + 1);
    }
    BitVector loop = node.loop;
    if (loop != null) {
      for (int i = 0; i < loop.length(); i++) {
        if (loop.get(i)) {
          BasicBlock bb = ir.getBasicBlock(i);
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
   * @param bb        The basic block to process
   * @param numBlocks The number of basic blocks
   */
  private void findBackEdges(BasicBlock bb, int numBlocks) {
    Stack<BasicBlock> stack = new Stack<BasicBlock>();
    SpaceEffGraphNode.OutEdgeEnumeration[] BBenum = new SpaceEffGraphNode.OutEdgeEnumeration[numBlocks];

    // push node on to the emulated activation stack
    stack.push(bb);

    recurse:
    while (!stack.empty()) {
      bb = stack.peek();

      // check if we were already processing this node, if so we would have
      // saved the state of the enumeration in the loop below
      SpaceEffGraphNode.OutEdgeEnumeration e = BBenum[bb.getNumber()];
      if (e == null) {
        if (DEBUG) { System.out.println(" Initial processing of " + bb); }
        bb.setDfsVisited();
        e = bb.outEdges();
      } else {
        if (DEBUG) { System.out.println(" Resuming processing of " + bb); }
      }

      while (e.hasMoreElements()) {
        SpaceEffGraphEdge outEdge = (SpaceEffGraphEdge) e.next();

        BasicBlock outbb = (BasicBlock) outEdge.toNode();
        if (LTDominatorInfo.isDominatedBy(bb, outbb)) {   // backedge
          outbb.setLoopHeader();
          outEdge.setBackEdge();
          if (DEBUG) {
            System.out.println("backedge from " +
                               bb.scratch +
                               " ( " + bb + " ) " +
                               outbb.scratch +
                               " ( " + outbb + " ) ");
          }
        } else if (!outbb.dfsVisited()) {
          // irreducible loop test
          if (outbb.scratch < bb.scratch) {
            throw new OptimizingCompilerException("irreducible loop found!");
          }
          // simulate a recursive call
          // but first save the enumeration state for resumption later
          BBenum[bb.getNumber()] = e;
          stack.push(outbb);
          continue recurse;
        }
      } // enum
      // "Pop" from the emulated activiation stack
      if (DEBUG) { System.out.println(" Popping"); }
      stack.pop();
    } // while !empty
  }

  /**
   * Clears the back edges for the basic block passed
   * @param bb the basic block
   */
  private void clearBackEdges(SpaceEffGraphNode bb) {
    SpaceEffGraphNode.OutEdgeEnumeration f = bb.outEdges();
    while (f.hasMoreElements()) {
      SpaceEffGraphEdge outEdge = (SpaceEffGraphEdge) f.next();
      outEdge.clearBackEdge();
    }
  }

  /**
   * This routine implements part of the algorithm to compute natural loops
   *  as defined in Muchnick p 192.  See caller for more details.
   * @param edge the edge to process
   * @param loop bit vector to hold the results of the algorithm
   */
  private void findNaturalLoop(SpaceEffGraphEdge edge, BitVector loop) {

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

    SpaceEffGraphNode fromNode = edge.fromNode();
    if (!fromNode.dfsVisited()) {
      fromNode.setDfsVisited();
      loop.set(fromNode.getNumber());
      for (SpaceEffGraphEdge in = fromNode.firstInEdge(); in != null; in = in.getNextIn()) {
        findNaturalLoop(in, loop);
      }
    }
  }
}
