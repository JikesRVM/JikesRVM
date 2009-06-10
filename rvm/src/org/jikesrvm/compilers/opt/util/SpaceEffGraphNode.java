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
package org.jikesrvm.compilers.opt.util;

import java.util.Enumeration;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * SpaceEffGraphNode is a generic graph node. Extend this to implement
 * specific graph node types.  A node has a list of out edges and a
 * list of in edges.  We maintain both to support bidirectional traversal
 * of the graph.
 */
public class SpaceEffGraphNode implements GraphNode {

  /** scratch field: optimizations use as they wish */
  public Object scratchObject;

  /** any optimization can use this for its own purposes */
  public int scratch;

  /**
   * The following word is used for various purposes. The first
   * 8 bits are used for flags, and the remaining 24 bits for any
   * node information (node number, for example)
   */
  protected int info;

  static final int DFS_VISITED = 0x01000000;
  static final int TOP_VISITED = 0x02000000;
  static final int ON_STACK = 0x04000000;

  static final int LOOP_HEADER = 0x08000000;

  static final int INFO_MASK = 0x00ffffff;

  public final boolean dfsVisited() { return (info & DFS_VISITED) != 0; }

  public final boolean topVisited() { return (info & TOP_VISITED) != 0; }

  public final boolean onStack() { return (info & ON_STACK) != 0; }

  public final boolean flagsOn() { return (info & (DFS_VISITED | TOP_VISITED | ON_STACK)) != 0; }

  public final boolean isLoopHeader() { return (info & LOOP_HEADER) != 0; }

  public final void setDfsVisited() { info |= DFS_VISITED; }

  public final void setTopVisited() { info |= TOP_VISITED; }

  public final void setOnStack() { info |= ON_STACK; }

  public final void setDfsVisitedOnStack() { info |= (DFS_VISITED | ON_STACK); }

  public final void setLoopHeader() { info |= LOOP_HEADER; }

  public final void clearDfsVisited() { info &= ~DFS_VISITED; }

  public final void clearTopVisited() { info &= ~TOP_VISITED; }

  public final void clearOnStack() { info &= ~ON_STACK; }

  public final void clearFlags() { info &= ~(DFS_VISITED | TOP_VISITED | ON_STACK); }

  public final void clearLoopHeader() { info &= ~LOOP_HEADER; }

  public int getScratch() { return scratch; }

  public int setScratch(int scratch) { return this.scratch = scratch; }

  public final void setNumber(int value) {
    info = (info & ~INFO_MASK) | (value & INFO_MASK);
  }

  public final int getNumber() {
    return info & INFO_MASK;
  }

  public final int getIndex() {
    return getNumber();
  }

  public final void setIndex(int i) {
    setNumber(i);
  }

  /////////////////
  // The following is used by several node sorting schemes
  /////////////////

  public SpaceEffGraphNode nextSorted;

  // return the first in/out edge

  public final SpaceEffGraphEdge firstInEdge() {
    return _inEdgeStart;
  }

  public final SpaceEffGraphEdge firstOutEdge() {
    return _outEdgeStart;
  }

  public final SpaceEffGraphNode firstInNode() {
    return _inEdgeStart.fromNode();
  }

  public final SpaceEffGraphNode firstOutNode() {
    return _outEdgeStart.toNode();
  }

  /**
   * clear the in set of edges
   */
  final void clearIn() {
    _inEdgeStart = _inEdgeEnd = null;
  }

  /**
   * clear the out set of edges
   */
  final void clearOut() {
    _outEdgeStart = _outEdgeEnd = null;
  }

  // deletes all the in/out edges

  public final void deleteIn() {
    for (SpaceEffGraphEdge e = _inEdgeStart; e != null; e = e.nextIn) {
      e.fromNode().removeOut(e);
    }
    clearIn();
  }

  public final void deleteOut() {
    for (SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.nextOut) {
      e.toNode().removeIn(e);
    }
    clearOut();
  }

  /* get number of in/out edges */

  public final int getNumberOfIn() {
    int count = 0;
    for (SpaceEffGraphEdge e = _inEdgeStart; e != null; e = e.nextIn) {
      count++;
    }
    return count;
  }

  public final int getNumberOfOut() {
    int count = 0;
    for (SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.nextOut) {
      count++;
    }
    return count;
  }

  /* specialized versions */
  public final boolean hasZeroOut() {
    return _outEdgeStart == null;
  }

  public final boolean hasZeroIn() {
    return _inEdgeStart == null;
  }

  public final boolean hasOneOut() {
    SpaceEffGraphEdge first = _outEdgeStart;
    return (first != null) && (first.nextOut == null);
  }

  public final boolean hasOneIn() {
    SpaceEffGraphEdge first = _inEdgeStart;
    return (first != null) && (first.nextIn == null);
  }

  /* returns true if points to the in/out set */

  public final boolean pointsIn(SpaceEffGraphNode inNode) {
    for (SpaceEffGraphEdge e = _inEdgeStart; e != null; e = e.nextIn) {
      if (e.fromNode() == inNode) return true;
    }
    return false;
  }

  public final boolean pointsOut(SpaceEffGraphNode outNode) {
    for (SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.nextOut) {
      if (e.toNode() == outNode) return true;
    }
    return false;
  }

  public final boolean hasIn(GraphNode in) {
    return pointsIn((SpaceEffGraphNode) in);
  }

  public final boolean hasOut(GraphNode out) {
    return pointsOut((SpaceEffGraphNode) out);
  }

  /*
   * returns the out edge pointing to node n, if it exists.
   * returns null otherwise
   */
  public final SpaceEffGraphEdge findOutEdgeTo(SpaceEffGraphNode n) {
    for (SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.nextOut) {
      if (e.toNode() == n) return e;
    }
    return null;
  }

  /*
   * replaces the in edge matching e1 with e2.
   * maintains the ordering of edges
   * YUCK: this data structure is messy.  I assume this is in the name
   * of efficiency, but it makes control flow graph manipulations
   * a real pain. (SJF)
   */
  public final void replaceInEdge(SpaceEffGraphEdge e1, SpaceEffGraphEdge e2) {
    // set the predecessor of e1 to point to e2
    if (_inEdgeStart == e1) {
      _inEdgeStart = e2;
    } else {
      // walk the list until we find the predecessor to e1
      SpaceEffGraphEdge pred = null;
      for (pred = _inEdgeStart; pred != null; pred = pred.nextIn) {
        if (pred.nextIn == e1) break;
      }
      // if not found, there's an error
      if (pred == null) {
        throw new OptimizingCompilerException("SpaceEffGraphNode.replaceInEdge: called incorrectly");
      }
      pred.nextIn = e2;
    }
    // set e2 to point to e1.nextIn
    e2.nextIn = e1.nextIn;

    // fix up _inEdgeStart, _inEdgeEnd
    if (_inEdgeStart == e1) _inEdgeStart = e2;
    if (_inEdgeEnd == e1) _inEdgeEnd = e2;

    // clear the links of e1
    e1.nextIn = null;
  }

  /* returns true if the node is the single predecessor/successor of
     this block */

  public final boolean hasOneIn(SpaceEffGraphNode inNode) {
    SpaceEffGraphEdge first = _inEdgeStart;
    return (first != null) && (first.nextIn == null) && (first.fromNode() == inNode);
  }

  public final boolean hasOneOut(SpaceEffGraphNode outNode) {
    SpaceEffGraphEdge first = _outEdgeStart;
    return (first != null) && (first.nextOut == null) && (first.toNode() == outNode);
  }

  /* replaces an oldnode with a new node */

  public final void replaceOut(SpaceEffGraphNode oldOut, SpaceEffGraphNode newOut) {
    deleteOut(oldOut);
    insertOut(newOut);
  }

  /* inserts an outgoing edge to a node 'to' */

  public final void insertOut(SpaceEffGraphNode to, SpaceEffGraphEdge e) {
    this.appendOutEdge(e);
    to.appendInEdge(e);
  }

  /* same as before, if you don't care the edge type */

  public final void insertOut(SpaceEffGraphNode to) {
    if (this.pointsOut(to)) return;
    SpaceEffGraphEdge e = new SpaceEffGraphEdge(this, to);
    this.appendOutEdge(e);
    to.appendInEdge(e);
  }

  /* delete an outgoing edge to a node */

  public final void deleteOut(SpaceEffGraphNode node) {
    SpaceEffGraphEdge edge = this.removeOut(node);
    node.removeIn(edge);
  }

  /* delete an outgoing edge  */

  public final void deleteOut(SpaceEffGraphEdge e) {
    SpaceEffGraphNode to = e.toNode();
    this.removeOut(e);
    to.removeIn(e);
  }

  /* mark nodes in a DFS manner, result written in 'scratch' */
  /* NOTE: it assummes that the 'dfs' flag has been cleared before */

  public final void markDFN(int DFN) {
    setDfsVisited();
    for (SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.nextOut) {
      SpaceEffGraphNode n = e.toNode();
      if (!n.dfsVisited()) {
        n.markDFN(DFN);
      }
    }
    scratch = DFN - 1;
  }

  /* mark nodes according to the SCC (Strongly Connected Component Number),
     result written in 'scratch'
     NOTE: it assummes that the 'dfs' flag has been cleared before */

  public final void markSCC(int currSCC) {
    setDfsVisited();
    scratch = currSCC;
    for (SpaceEffGraphEdge e = _inEdgeStart; e != null; e = e.nextIn) {
      SpaceEffGraphNode n = e.fromNode();
      if (!n.dfsVisited()) {
        n.markSCC(currSCC);
      }
    }
  }

  /* sort nodes according to DFS. result is a list of nodes with the current
     as root.  Note: it assumes that the dfs flags have been cleared before */

  public final void sortDFS() {
    _sortDFS(null);
  }

  protected final void _sortDFS(SpaceEffGraphNode header) {
    setDfsVisited();
    for (SpaceEffGraphEdge e = _outEdgeStart; e != null; e = e.nextOut) {
      SpaceEffGraphNode n = e.toNode();
      if (!n.dfsVisited()) {
        n._sortDFS(header);
        header = n;
      }
    }
    nextSorted = header;
  }

  /* clear all out/in flags */

  public final void clearOutFlags() {
    clearFlags();
    for (SpaceEffGraphEdge e = firstOutEdge(); e != null; e = e.getNextOut()) {
      SpaceEffGraphNode succ = e.toNode();
      e.clearVisited();
      if (succ.flagsOn()) {
        succ.clearOutFlags();
      }
    }
  }

  public final void clearInFlags() {
    clearFlags();
    for (SpaceEffGraphEdge e = firstInEdge(); e != null; e = e.getNextIn()) {
      SpaceEffGraphNode succ = e.fromNode();
      e.clearVisited();
      if (succ.flagsOn()) {
        succ.clearInFlags();
      }
    }
  }

  /* topological sort of nodes. result is a list of nodes with the current
     as root */

  public final void sortTop() {
    clearOutFlags();
    setDfsVisitedOnStack();
    nextSorted = _sortTop(null);
  }

  protected final SpaceEffGraphNode _sortTop(SpaceEffGraphNode tail) {
    for (SpaceEffGraphEdge e = firstOutEdge(); e != null; e = e.getNextOut()) {
      SpaceEffGraphNode succ = e.toNode();
      if (!succ.dfsVisited()) {
        succ.setDfsVisitedOnStack();
        tail = succ._sortTop(tail);
      } else if (succ.onStack() || succ == this) {
        e.setVisited(); // back edge
      }
    }
    clearOnStack();
    for (SpaceEffGraphEdge e = firstOutEdge(); e != null; e = e.getNextOut()) {
      SpaceEffGraphNode succ = e.toNode();
      if (!succ.topVisited() && !e.visited()) {
        succ.nextSorted = tail;
        tail = succ;
        succ.setTopVisited();
      }
    }
    return tail;
  }

  /* reverse topological sort of nodes. result is a list of nodes with the
     current as root */

  public final void sortRevTop() {
    clearInFlags();
    setDfsVisitedOnStack();
    nextSorted = _sortRevTop(null);
  }

  protected final SpaceEffGraphNode _sortRevTop(SpaceEffGraphNode tail) {
    for (SpaceEffGraphEdge e = firstInEdge(); e != null; e = e.getNextIn()) {
      SpaceEffGraphNode succ = e.fromNode();
      if (!succ.dfsVisited()) {
        succ.setDfsVisitedOnStack();
        tail = succ._sortRevTop(tail);
      } else if (succ.onStack() || succ == this) {
        e.setVisited(); // forward edge
      }
    }
    clearOnStack();
    for (SpaceEffGraphEdge e = firstInEdge(); e != null; e = e.getNextIn()) {
      SpaceEffGraphNode succ = e.fromNode();
      if (!succ.topVisited() && !e.visited()) {
        succ.nextSorted = tail;
        tail = succ;
        succ.setTopVisited();
      }
    }
    return tail;
  }

  /* print sorted nodes starting from this */

  final void printSorted() {
    for (SpaceEffGraphNode n = this; n != null; n = n.nextSorted) {
      System.out.println(n);
    }
  }

  /**
   * Revert the sequence of out edges
   */
  final void revertOuts() {
    SpaceEffGraphEdge last = null;
    SpaceEffGraphEdge e = firstOutEdge();
    _outEdgeStart = _outEdgeEnd;
    _outEdgeEnd = e;
    while (e != null) {
      SpaceEffGraphEdge next = e.getNextOut();
      e.appendOut(last);
      last = e;
      e = next;
    }
  }

  /* enumerations to get the nodes/edges */

  public interface GraphEdgeEnumeration<T extends GraphEdge> extends Enumeration<T> {
    // Same as nextElement but avoid the need to downcast from Object
    T next();
  }

  public final InEdgeEnumeration inEdges() {
    return new InEdgeEnumeration(this);
  }

  public final OutEdgeEnumeration outEdges() {
    return new OutEdgeEnumeration(this);
  }

  public final GraphNodeEnumeration inNodes() {
    return new InNodeEnumeration(this);
  }

  public final GraphNodeEnumeration outNodes() {
    return new OutNodeEnumeration(this);
  }

  /* print utilities */

  public void printInEdges() {
    for (SpaceEffGraphEdge in = firstInEdge(); in != null; in = in.getNextIn()) {
      System.out.println(in.fromNodeString());
    }
  }

  public void printOutEdges() {
    for (SpaceEffGraphEdge out = firstOutEdge(); out != null; out = out.getNextOut()) {
      System.out.println(out.toNodeString());
    }
  }

  public void printInNodes() {
    for (SpaceEffGraphEdge in = firstInEdge(); in != null; in = in.getNextIn()) {
      System.out.println(in.fromNode());
    }
  }

  public void printOutNodes() {
    for (SpaceEffGraphEdge out = firstOutEdge(); out != null; out = out.getNextOut()) {
      System.out.println(out.toNode());
    }
  }

  public void printExtended() {
    System.out.println(this);
  }

  /////////////////
  // Implementation: the following is not intended for general client use
  /////////////////

  protected SpaceEffGraphEdge _outEdgeStart;
  protected SpaceEffGraphEdge _outEdgeEnd;
  protected SpaceEffGraphEdge _inEdgeStart;
  protected SpaceEffGraphEdge _inEdgeEnd;

  //
  // add an in/out edge from 'node' to this node.
  //

  // (SJF): I had to make these public to do SSA transformations.
  // TODO: The CFG data structure should not depend this tightly
  // on the underlying Graph implementation, but rather should be
  // designed so that the SSA-like transformations are easy to do.

  protected final void appendInEdge(SpaceEffGraphEdge e) {
    SpaceEffGraphEdge inEdgeEnd = _inEdgeEnd;
    if (inEdgeEnd != null) {
      inEdgeEnd.appendIn(e);
    } else {
      _inEdgeStart = e;
    }
    _inEdgeEnd = e;
  }

  protected final void appendOutEdge(SpaceEffGraphEdge e) {
    SpaceEffGraphEdge outEdgeEnd = _outEdgeEnd;
    if (outEdgeEnd != null) {
      outEdgeEnd.appendOut(e);
    } else {
      _outEdgeStart = e;
    }
    _outEdgeEnd = e;
  }

  /* remove and edge/node from the in/out set */

  protected final void removeIn(SpaceEffGraphEdge InEdge) {
    SpaceEffGraphEdge prev = null;
    for (SpaceEffGraphEdge edge = _inEdgeStart; edge != null; prev = edge, edge = edge.nextIn) {
      if (edge == InEdge) {
        SpaceEffGraphEdge next = edge.nextIn;
        if (prev == null) {
          _inEdgeStart = next;
        } else {
          prev.appendIn(next);
        }
        if (next == null) {
          _inEdgeEnd = prev;
        }
        break;
      }
    }
  }

  protected final SpaceEffGraphEdge removeIn(SpaceEffGraphNode InNode) {
    SpaceEffGraphEdge edge, prev = null;
    for (edge = _inEdgeStart; edge != null; prev = edge, edge = edge.nextIn) {
      if (edge.fromNode() == InNode) {
        SpaceEffGraphEdge next = edge.nextIn;
        if (prev == null) {
          _inEdgeStart = next;
        } else {
          prev.appendIn(next);
        }
        if (next == null) {
          _inEdgeEnd = prev;
        }
        break;
      }
    }
    return edge;
  }

  protected final void removeOut(SpaceEffGraphEdge OutEdge) {
    SpaceEffGraphEdge edge, prev = null;
    for (edge = _outEdgeStart; edge != null; prev = edge, edge = edge.nextOut) {
      if (edge == OutEdge) {
        SpaceEffGraphEdge next = edge.nextOut;
        if (prev == null) {
          _outEdgeStart = next;
        } else {
          prev.appendOut(next);
        }
        if (next == null) {
          _outEdgeEnd = prev;
        }
        break;
      }
    }
  }

  protected final SpaceEffGraphEdge removeOut(SpaceEffGraphNode OutNode) {
    SpaceEffGraphEdge edge, prev = null;
    for (edge = _outEdgeStart; edge != null; prev = edge, edge = edge.nextOut) {
      if (edge.toNode() == OutNode) {
        SpaceEffGraphEdge next = edge.nextOut;
        if (prev == null) {
          _outEdgeStart = next;
        } else {
          prev.appendOut(next);
        }
        if (next == null) {
          _outEdgeEnd = prev;
        }
        break;
      }
    }
    return edge;
  }

  static final class InEdgeEnumeration implements GraphEdgeEnumeration<GraphEdge> {
    private SpaceEffGraphEdge _edge;

    public InEdgeEnumeration(SpaceEffGraphNode n) {
      _edge = n._inEdgeStart;
    }

    public boolean hasMoreElements() { return _edge != null; }

    public SpaceEffGraphEdge nextElement() { return next(); }

    public SpaceEffGraphEdge next() {
      SpaceEffGraphEdge e = _edge;
      _edge = e.nextIn;
      return e;
    }
  }

  static final class InNodeEnumeration implements GraphNodeEnumeration {
    private SpaceEffGraphEdge _edge;

    public InNodeEnumeration(SpaceEffGraphNode n) {
      _edge = n._inEdgeStart;
    }

    public boolean hasMoreElements() { return _edge != null; }

    public GraphNode nextElement() { return next(); }

    public GraphNode next() {
      SpaceEffGraphEdge e = _edge;
      _edge = e.nextIn;
      return e.fromNode();
    }
  }

  public static final class OutEdgeEnumeration implements GraphEdgeEnumeration<GraphEdge> {
    private SpaceEffGraphEdge _edge;

    public OutEdgeEnumeration(SpaceEffGraphNode n) {
      _edge = n._outEdgeStart;
    }

    public boolean hasMoreElements() { return _edge != null; }

    public GraphEdge nextElement() { return next(); }

    public GraphEdge next() {
      SpaceEffGraphEdge e = _edge;
      _edge = e.nextOut;
      return e;
    }
  }

  static final class OutNodeEnumeration implements GraphNodeEnumeration {
    private SpaceEffGraphEdge _edge;

    public OutNodeEnumeration(SpaceEffGraphNode n) {
      _edge = n._outEdgeStart;
    }

    public boolean hasMoreElements() { return _edge != null; }

    public GraphNode nextElement() { return next(); }

    public GraphNode next() {
      SpaceEffGraphEdge e = _edge;
      _edge = e.nextOut;
      return e.toNode();
    }
  }

  /**
   * Links inlined from DoublyLinkedListElement.
   */
  public SpaceEffGraphNode prev, next;

  /**
   * Get the next node.
   * @return next node
   */
  public final SpaceEffGraphNode getNext() {
    return next;
  }

  /**
   * Get the previous node.
   * @return previous node
   */
  public final SpaceEffGraphNode getPrev() {
    return prev;
  }

  /**
   * Append a given node after this node.
   * @param n the node to append
   */
  public final void append(SpaceEffGraphNode n) {
    next = n;
    n.prev = this;
  }

  /**
   * Remove this node from the list.
   * @return the next node in the list
   */
  public final SpaceEffGraphNode remove() {
    // copy old links
    SpaceEffGraphNode Prev = prev, Next = next;

    // reset old links
    prev = null;
    next = null;

    // compute new links
    if (Prev != null) Prev.next = Next;
    if (Next != null) Next.prev = Prev;

    // return next node
    return Next;
  }
}


