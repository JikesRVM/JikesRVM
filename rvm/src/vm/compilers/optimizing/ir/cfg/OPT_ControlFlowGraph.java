/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.*;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * The Factored Control Flow Graph (FCFG). 
 * <p>
 * Like a standard control flow graph (CFG), the FCFG is composed
 * of {@link OPT_BasicBlock basic blocks} which in turn contain
 * {@link OPT_Instruction instructions}. The critical difference between
 * a FCFG and a CFG is in the definition of basic blocks.  In the FCFG,
 * a Potentially Excepting Instruction (PEI) does not necessarily end its
 * basic block.  Therefore, although instructions within a FCFG basic block 
 * have the expected dominance relationships, they do <em>not</em> have the
 * same post-dominance relationships as they would under the traditional
 * basic block formulation used in a CFG.  
 * We chose to use an FCFG because doing so significantly reduces the
 * number of basic blocks and control flow graph edges, thus reducing
 * the time and space costs of representing the FCFG and also 
 * increasing the effectiveness of local (within a single basic block)
 * analysis.  However, using an FCFG does complicate flow-sensitive
 * global analaysis.  Many analyses can be easily extended to
 * work on the FCFG.  For those analyses that cannot, we provide utilities
 * ({@link OPT_IR#unfactor()}, {@link OPT_BasicBlock#unfactor(OPT_IR)})
 * to effectively convert the FCFG into a CFG.  
 * For a more detailed description of the FCFG and its implications for
 * program analysis see the PASTE'99 paper by Choi et al. 
 *   <a href="http://www.research.ibm.com/jalapeno/publication.html#paste99">
 *   Efficient and Precise Modeling of Exceptions for the Analysis of Java Programs </a>
 * <p>
 * The nodes in the FCFG are components in two distinct 
 * orderings, the "main" one is the control flow relationship 
 * in which edges represent flow of control.
 * The secondary ordering is a linearization of the blocks
 * which represents the ordering of instructions in the generated code.
 * Both of these relationships are represented using the fields inherited 
 * from {@link OPT_SpaceEffGraphNode}.
 * The control flow edges are the primary relationship and are encoded by 
 * <code>In</code> and <code>Out</code> relations of 
 * {@link OPT_SpaceEffGraphNode} and the {@link #entry()} and {@link #exit()}
 * functions of <code>OPT_ControlFlowGraph</code>.  
 * The linear order is secondary and is represented by the order of the 
 * nodes in the doubly linked list ({@link OPT_SpaceEffGraphNode#next} and 
 * {@link OPT_SpaceEffGraphNode#prev}) and the functions
 * ({@link #firstInCodeOrder()}, {@link #lastInCodeOrder()})
 * of <code>OPT_ControlFlowGraph<code>.
 * Utility functions are provided here and in {@link OPT_SpaceEffGraphNode}
 * to manipulate these orderings. 
 *
 * @see OPT_BasicBlock
 * @see OPT_IR
 * 
 * @author Dave Grove
 * @author Mauricio Serrano
 * @author John Whaley
 */
public final class OPT_ControlFlowGraph extends OPT_SpaceEffGraph {

  /**
   * The distringuished exit node of the FCFG
   */
  private OPT_BasicBlock _exitNode;

  /**
   * Return the entry node of the FCFG.  All reachable nodes
   * can be found by doing a forward traversal from this node.
   * 
   * @return the entry node of the FCFG
   */
  public OPT_BasicBlock entry() {
    return (OPT_BasicBlock)_firstNode;
  }
  
  /**
   * Return the "exit" node of the FCFG.  In a perfect world,
   * we'd have the invariant that all nodes that are reachable in a 
   * forward traversal from cfgEntry() are exactly the same set of nodes
   * as those that are reachable from cfgExit() via a reverse traversal,
   * but that's currently not the case.  Not all forward reachable nodes can
   * be found by going backwards from exit.  The issue is infinite loops 
   * (loops without normal exits).
   * 
   * @return the exit node of the FCFG
   */
  public OPT_BasicBlock exit() {
    return (OPT_BasicBlock)_exitNode;
  }


  /**
   * Return the first basic block with respect to 
   * the current code linearization order.
   * 
   * @return the first basic block in the code order
   */
  public OPT_BasicBlock firstInCodeOrder() {
    return (OPT_BasicBlock)_firstNode;
  }


  /**
   * Return the last basic block with respect to 
   * the current code linearization order.
   * 
   * @return the last basic block in the code order
   */
  public OPT_BasicBlock lastInCodeOrder() {
    return (OPT_BasicBlock)_lastNode;
  }


  /**
   * Return the node to start with for a topological traversal
   * of the FCFG. 
   * Override {@link OPT_SpaceEffGraph#startNode(boolean)} 
   * to use entry and exit; we want topological traversals to be with 
   * respect to FCFG edges not the code linearization order
   * 
   * @param forward  true for forward traversal, false for reverse
   * @return the node to use as the start of a topological traversal
   */
  public OPT_SortedGraphNode startNode(boolean forward) {
    if (forward)
      return entry();
    else
      return exit();
  }

  /**
   * Densely number (0...n) all nodes in the FCFG.
   * Override {@link OPT_SpaceEffGraph#compactNodeNumbering()} to also 
   * number the exit node.
   */
  public void compactNodeNumbering() {
    super.compactNodeNumbering();
    exit().setNumber(numberOfNodes++);
  }

  /**
   * Builds the reverse topological order, i.e., the topsort order on the
   * reverse graph.  (This is not the same as reversing the topsort order
   * of the forward graph.)
   *
   * @return the first node in the reverse topological ordering
   */
  public OPT_SortedGraphNode buildRevTopSort() {
    OPT_SortedGraphNode firstNode = super.buildRevTopSort();
    if (firstNode != null) {

      // The CFG may have "end" nodes that are not reachable
      // by all nodes.  For example, a program with an infinite loop will not
      // have a path from the loop to the exit node.  Such nodes will not
      // be in the reverseTopSort, but will be of interest.  Thus, we now
      // look for such nodes and add them to the revTopSort.

      // We do this by visiting each basic block and checking to ensure
      // that is marked with the sortMarker, if not we simply give it a
      // number.

      int sortMarker = firstNode.getSortMarker();
      int sortNumber = firstNode.getBackwardSortNumber() - 1;
      for (OPT_BasicBlock block = firstInCodeOrder();
           block != null;
           block = block.nextBasicBlockInCodeOrder()) {

        if (block.getSortMarker() != sortMarker) {
          // found a block that wasn't on the Reverse Top List, add it.
          // It is not clear where it should go, so since it is convenient
          // to add at the front, we add it at the front!
          block.setSortMarker(sortMarker);
          block.setBackwardSortNumber(sortNumber--);

          // put block at the beginning of the list
          block.setSortedNext(firstNode, false);
          firstNode = block;
        }
      }
    }
    return firstNode;
  }


  /**
   * @param number starting value for assigning node numbers
   */
  OPT_ControlFlowGraph(int number) {
    _exitNode = OPT_BasicBlock.makeExit();
    numberOfNodes = number;
  }


  /**
   * Add an FCFG edge from the given basic block to the exit node.
   * 
   * @param bb basic block to link to the exit
   */
  public void linkToExit(OPT_BasicBlock bb) {
     bb.insertOut(exit());
  }


  /**
   * Remove a basic block from both the CFG and code ordering
   * 
   * @param bb the block to remove
   */
  public void removeFromCFGAndCodeOrder(OPT_BasicBlock bb) {
    removeFromCFG(bb);
    removeFromCodeOrder(bb);
  }

  /** 
   * Remove a basic block from the FCFG, leaving the code ordering unchanged.
   * 
   * @param bb the block to remove
   */
  public void removeFromCFG(OPT_BasicBlock bb) {
    bb.deleteIn();
    bb.deleteOut();
  }

  /**
   * Remove a basic block from the code ordering, 
   * leaving the FCFG unchanged.
   *
   * @param bb the block to remove
   */
  public void removeFromCodeOrder(OPT_BasicBlock bb) {
    if (bb == _firstNode) {
      _firstNode = bb.getNext();
    }
    if (bb == _lastNode) {
      _lastNode = bb.getPrev();
    }
    bb.remove();
  }


  /**
   * Insert a block 'toAdd' not currently in the code ordering after
   * a block 'old' that is currently in the code ordering. 
   * If necessary, _lastNode is updated.
   * No impact on FCFG edges.
   * 
   * @param old a block currently in the code ordering
   * @param toAdd a block to add after old in the code ordering
   */
  public void insertAfterInCodeOrder(OPT_BasicBlock old, OPT_BasicBlock toAdd) {
    if (OPT_IR.SANITY_CHECK) VM._assert(toAdd.next == null);
    if (OPT_IR.SANITY_CHECK) VM._assert(toAdd.prev == null);
    OPT_SpaceEffGraphNode oldNext = old.next;
    if (oldNext == null) {
      if (OPT_IR.SANITY_CHECK) VM._assert(_lastNode == old);
      old.append(toAdd);
      _lastNode = toAdd;
    } else {
      old.append(toAdd);
      toAdd.append(oldNext);
    }
  }


  /**
   * Insert a block 'toAdd' not currently in the code ordering before
   * a block 'old' that is currently in the code ordering. 
   * If necessary, _firstNode is updated.
   * No impact on FCFG edges.
   * 
   * @param old a block currently in the code ordering
   * @param toAdd a block to add before old in the code ordering
   */
  public void insertBeforeInCodeOrder(OPT_BasicBlock old, OPT_BasicBlock toAdd) {
    if (OPT_IR.SANITY_CHECK) VM._assert(toAdd.next == null);
    if (OPT_IR.SANITY_CHECK) VM._assert(toAdd.prev == null);
    OPT_SpaceEffGraphNode oldPrev = old.prev;
    if (oldPrev == null) {
      if (OPT_IR.SANITY_CHECK) VM._assert(_firstNode == old);
      _firstNode = toAdd;
      toAdd.append(old);
    } else {
      oldPrev.append(toAdd);
      toAdd.append(old);
    }
  }
    

  /**
   * Add a block not currently in the code ordering to the end of the 
   * code ordring.
   * No impact on FCFG edges.
   *
   * @param bb the block to add to the end of the code ordering
   */
  public void addLastInCodeOrder(OPT_BasicBlock bb) {
    if (OPT_IR.SANITY_CHECK) VM._assert(bb.next == null);
    if (OPT_IR.SANITY_CHECK) VM._assert(bb.prev == null);
    if (_firstNode == null) {
      _firstNode = bb;
      _lastNode = bb;
    } else {
      _lastNode.append(bb);
      _lastNode = bb;
    }
  }


  /**
   * Make BB1 follow BB2 in the code ordering.
   * If _lastNode == BB1, then update BB1 appropriately
   * No impact on FCFG edges.
   * 
   * @param bb1 a basic block
   * @param bb2 the basic block to follow bb1 in the code ordering
   */
  public void linkInCodeOrder(OPT_BasicBlock bb1, OPT_BasicBlock bb2) {
    if (OPT_IR.SANITY_CHECK) VM._assert(bb1.next == null);
    if (OPT_IR.SANITY_CHECK) VM._assert(bb2.prev == null);
    bb1.append(bb2);
    if (bb1 == _lastNode) {
      _lastNode = bb2;
    }
  }


  /**
   * Create a break in the code order between bb1 and bb2
   * (bb1 and bb2 must be currently adjacent in the code order).
   * No impact on FCFG edges.
   *
   * @param bb1 the first block
   * @param bb2 the second block
   */
  public void breakCodeOrder(OPT_BasicBlock bb1, OPT_BasicBlock bb2) {
    if (OPT_IR.SANITY_CHECK) VM._assert(bb1.next == bb2);
    if (OPT_IR.SANITY_CHECK) VM._assert(bb2.prev == bb1);
    bb1.next = null;
    bb2.prev = null;
  }

  /**
   * Clear the code ordering information for the CFG.
   * NOTE: This method should only be called as part of a 
   *       whole scale recomputation of the code order, for example
   *       by OPT_ReorderingPhase
   */
  public void clearCodeOrder() {
    OPT_SpaceEffGraphNode cur = _firstNode;
    if (cur == null) return;
    while (true) {
      OPT_SpaceEffGraphNode next = cur.next;
      if (next == null) break;
      cur.next = null;
      next.prev = null;
      cur = next;
    }
    _firstNode = null;
    _lastNode = null;
  }

  // VCG Graph stuff (visualization of FCFG using VCG tool)
  private static final class NodeEnumeration implements Enumeration {
    private OPT_SpaceEffGraphNode  _node;
    private OPT_SpaceEffGraphNode  _end;
    public NodeEnumeration(OPT_ControlFlowGraph cfg) { 
      _node = cfg.entry();
      _end=cfg.exit(); 
    }
    public boolean hasMoreElements() { return _node != null; }
    public Object nextElement() {
      OPT_SpaceEffGraphNode n = _node;
      _node = n.getNext();
      if ((n != _end) && (_node == null)) 
        _node = _end;
      return n;
    }
  }

  // implements OPT_VCGGraph
  public Enumeration nodes() { return new NodeEnumeration(this); }
}
