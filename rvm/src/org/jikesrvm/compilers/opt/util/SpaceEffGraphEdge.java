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


/**
 * SpaceEffGraphEdge is a generic graph edge.  Extend this to implement
 * specific graph edge types, or use it as a generic edge.
 * SpaceEffGraphEdges are directed, and therefore, have a from-node and
 * a to-node.
 */
public class SpaceEffGraphEdge implements GraphEdge {
  /**
   * End node.
   */
  protected SpaceEffGraphNode _toNode;

  /**
   * Start node.
   */
  protected SpaceEffGraphNode _fromNode;

  /**
   * The following word is defined for several uses.  The first 4 bits
   * are reserved for SpaceEffGraph.  Classes that subclass this one
   * can use the remaining 28 bits
   */
  protected int scratch;

  static final int VISITED = 0x10000000; // general purpose

  static final int BACK_EDGE = 0x20000000; // edge information
  static final int DOMINATOR = 0x40000000; // edge information

  static final int INFO_MASK = 0x0fffffff;

  public final boolean visited() { return (scratch & VISITED) != 0; }

  public final boolean backEdge() { return (scratch & BACK_EDGE) != 0; }

  public final boolean dominatorEdge() { return (scratch & DOMINATOR) != 0; }

  public final void setVisited() { scratch |= VISITED; }

  public final void setBackEdge() { scratch |= BACK_EDGE; }

  public final void setDominatorEdge() { scratch |= DOMINATOR; }

  public final void clearVisited() { scratch &= ~VISITED; }

  public final void clearBackEdge() { scratch &= ~BACK_EDGE; }

  public final void clearDominatorEdge() { scratch &= ~DOMINATOR; }

  public final int getInfo() {
    return scratch & INFO_MASK;
  }

  public final void setInfo(int value) {
    scratch = (scratch & ~INFO_MASK) | (value & INFO_MASK);
  }

  /**
   * Get the end node for the edge.
   * @return end node for the edge
   */
  public final SpaceEffGraphNode toNode() { return _toNode; }

  /**
   * Get the start node for the edge.
   * @return start node for the edge
   */
  public final SpaceEffGraphNode fromNode() { return _fromNode; }

  /**
   * Set end node.
   * WARNING: use with caution
   * @param toNode new end node
   */
  final void setToNode(SpaceEffGraphNode toNode) { _toNode = toNode; }

  /**
   * Set start node.
   * WARNING: use with caution
   * @param fromNode new start node
   */
  final void setFromNode(SpaceEffGraphNode fromNode) {
    _fromNode = fromNode;
  }

  /**
   * Constructs an empty edge.
   */
  SpaceEffGraphEdge() { }

  /**
   * Constructs an edge starting at a given node and ending at a given node.
   * @param fromNode start node
   * @param toNode end node
   */
  protected SpaceEffGraphEdge(SpaceEffGraphNode fromNode, SpaceEffGraphNode toNode) {
    _toNode = toNode;
    _fromNode = fromNode;
  }

  /**
   * Delete this edge from the graph.
   */
  final void delete() {
    _fromNode.removeOut(this);
    _toNode.removeIn(this);
  }

  /**
   * Returns the string representation of the edge type.
   * @return string representation of the edge type
   */
  public String getTypeString() { return ""; }

  /**
   * Returns the string representation of the end node (used for printing).
   * @return string representation of the end node
   */
  public String toNodeString() {
    return "---> " + _toNode;
  }

  /**
   * Returns the string representation of the start node (used for printing).
   * @return string representation of the start node
   */
  public String fromNodeString() {
    return "<--- " + _fromNode;
  }

  /**
   * Get the end node for the edge.
   * @return end node for the edge
   */
  public final GraphNode to() { return _toNode; }

  /**
   * Get the start node for the edge.
   * @return start node for the edge
   */
  public final GraphNode from() { return _fromNode; }

  /**
   * Links inlined from LinkedListElement2.
   */
  protected SpaceEffGraphEdge nextIn, nextOut;

  /**
   * Get the next in edge.
   * @return next in edge.
   */
  public final SpaceEffGraphEdge getNextIn() { return nextIn; }

  /**
   * Get the next out edge.
   * @return next out edge.
   */
  public final SpaceEffGraphEdge getNextOut() { return nextOut; }

  /**
   * Append a given edge after this edge as an in edge.
   * @param e the edge to append
   */
  final void appendIn(SpaceEffGraphEdge e) { nextIn = e; }

  /**
   * Append a given edge after this edge as an out edge.
   * @param e the edge to append
   */
  final void appendOut(SpaceEffGraphEdge e) { nextOut = e; }
}

