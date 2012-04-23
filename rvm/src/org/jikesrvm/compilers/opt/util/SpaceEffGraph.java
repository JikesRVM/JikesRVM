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
import java.util.HashSet;


/**
 * SpaceEffGraph package implements a generic directed graph that can
 * be a multigraph. It uses Lists to model nodes and edges information.
 *
 * SpaceEffGraph is a generic graph.  Extend to implement specific
 * graph types.
 */
public class SpaceEffGraph implements Graph, TopSortInterface {
  /**
   * First node
   */
  protected SpaceEffGraphNode _firstNode;
  /**
   * Last node
   */
  protected SpaceEffGraphNode _lastNode;
  /**
   * Nodes with no predecessors
   */
  private SpaceEffGraphNodeListHeader _rootNodes;
  /**
   * Topological sort order
   */
  private SpaceEffGraphNodeListHeader _topSortNodes; // top sort order.

  /**
   * Number of nodes
   */
  protected int numberOfNodes;

  /**
   * Get number of nodes
   * @return number of nodes
   */
  @Override
  public final int numberOfNodes() { return numberOfNodes; }

  /**
   * Set number of nodes
   * @param n new number of nodes
   */
  public final void setNumberOfNodes(int n) { numberOfNodes = n; }

  /**
   * Get the next node number
   * @return the node number
   */
  public final int allocateNodeNumber() { return numberOfNodes++; }

  /**
   * Renumber the nodes densely from 0...numberOfNodes-1.
   */
  @Override
  public void compactNodeNumbering() {
    int number = 0;
    for (SpaceEffGraphNode n = _firstNode; n != null; n = n.getNext()) {
      n.setNumber(number++);
    }
    numberOfNodes = number;
  }

  /**
   * Enumerate the nodes in no particular order
   */
  @Override
  public GraphNodeEnumeration enumerateNodes() {
    return new NodeEnumeration(_firstNode);
  }

  //////////////////
  // The following are to implement TopSortInterface.
  //////////////////

  @Override
  public SortedGraphNode startNode(boolean forward) {
    if (forward) {
      return (SortedGraphNode) _firstNode;
    } else {
      return (SortedGraphNode) _lastNode;
    }
  }

  @Override
  public boolean isTopSorted(boolean forward) {
    if (forward) {
      return forwardTopSorted;
    } else {
      return backwardTopSorted;
    }
  }

  @Override
  public void setTopSorted(boolean forward) {
    if (forward) {
      forwardTopSorted = true;
    } else {
      backwardTopSorted = true;
    }
  }

  @Override
  public void resetTopSorted() {
    forwardTopSorted = false;
    backwardTopSorted = false;
  }

  public boolean forwardTopSorted = false, backwardTopSorted = false;

  //////////////////
  // End of TopSortInterface implementation
  //////////////////

  /**
   * Add a node to the graph.
   * @param inode node to add
   */
  @Override
  public final void addGraphNode(GraphNode inode) {
    SpaceEffGraphNode node = (SpaceEffGraphNode) inode;
    //_nodes.add(node);
    if (_firstNode == null) {
      _firstNode = node;
      _lastNode = node;
    } else {
      _lastNode.append(node);  // this is cheaper than add() call.
      _lastNode = node;
    }
    numberOfNodes++;
  }

  /**
   * Remove a node from the graph.
   * @param node node to remove
   */
  public final void removeGraphNode(SpaceEffGraphNode node) {
    if (node == _firstNode) {
      if (node == _lastNode) {
        _firstNode = _lastNode = null;
      } else {
        _firstNode = node.getNext();
      }
    } else if (node == _lastNode) {
      _lastNode = node.getPrev();
    }
    node.remove();
    numberOfNodes--;
  }

  /**
   * Add an edge to the graph.
   * @param from start node
   * @param to end node
   * @see #addGraphEdge(SpaceEffGraphEdge)
   */
  @Override
  public void addGraphEdge(GraphNode from, GraphNode to) {
    ((SpaceEffGraphNode) from).insertOut((SpaceEffGraphNode) to);
  }

  /**
   * Add an edge to the graph.
   * @param e edge to insert
   * @see #addGraphEdge(GraphNode,GraphNode)
   */
  public void addGraphEdge(SpaceEffGraphEdge e) {
    e.fromNode().appendOutEdge(e);
    e.toNode().appendInEdge(e);
  }

  /**
   * Reset the list of nodes of the graph.
   * WARNING!!!  Use with caution if you know what you are doing.
   * @param firstNode new value of the node list
   */
  public final void setFirstNode(SpaceEffGraphNode firstNode) {
    _firstNode = firstNode;
  }

  /**
   * Reset the list of nodes of the graph.
   * WARNING!!!  Use with caution if you know what you are doing.
   * @param lastNode new value of the node list
   */
  public final void setLastNode(SpaceEffGraphNode lastNode) {
    _lastNode = lastNode;
  }
  /**
   * Get the list of nodes.
   * @return list of nodes
   */
  public final SpaceEffGraphNode firstNode() {
    return _firstNode;
  }

  /**
   * Get the end of the list of nodes.
   * @return end of the list of nodes
   */
  public final SpaceEffGraphNode lastNode() {
    return _lastNode;
  }

  /**
   * Add a root node to the graph.
   * @param root a node to add
   */
  public final void addRootNode(SpaceEffGraphNode root) {
    //_rootNodes.add(root);
    if (_rootNodes == null) {
      _rootNodes = new SpaceEffGraphNodeListHeader();
    }
    _rootNodes.append(root);
  }

  /**
   * Get the list of root nodes.
   * @return list of root nodes
   */
  public final SpaceEffGraphNodeList rootNodes() {
    return _rootNodes.first();
  }

  /**
   * Get the topological order of nodes.
   * @return topological order of nodes
   */
  public final SpaceEffGraphNodeList topSortOrder() {
    return _topSortNodes.first();
  }

  /**
   * Clear the DFS flags.
   */
  public final void clearDFS() {
    for (SpaceEffGraphNode n = firstNode(); n != null; n = n.getNext()) {
      n.clearDfsVisited();
    }
  }

  /**
   * Build a topological sort of this graph
   */
  public void buildTopSort() {
    if (!forwardTopSorted) {
      //SortedGraphNode node =
      TopSort.buildTopological(this, true);
      // currently, no one cares about the return value, so we don't return it
    }
  }

  /**
   * Build a reverse topological sort of this graph
   * @return a node if we build a new order, null if we reused the old
   */
  public SortedGraphNode buildRevTopSort() {
    if (!backwardTopSorted) {
      return TopSort.buildTopological(this, false);
    } else {
      return null;
    }
  }

  ///////////////////////
  // Starting with the root nodes, topologically sort them using
  // the out edge information. Builds the _topSortNodes list.
  // TODO: figure out how this works and add comments (IP)
  ///////////////////////

  protected void initTopSort() {
    _topSortNodes = new SpaceEffGraphNodeListHeader();
  }

  protected void addTopSortNode(SpaceEffGraphNode node) {
    _topSortNodes.append(node);
  }

  public void topSort() {
    initTopSort();
    for (SpaceEffGraphNode n = firstNode(); n != null; n = n.getNext()) {
      if (n.firstInEdge() == null) { // no predecessors
        n.setDfsVisited();
        n.setOnStack();
        dfs(n);
        addTopSortNode(n);
      }
    }
  }

  private void dfs(SpaceEffGraphNode node) {
    for (SpaceEffGraphEdge edge = node.firstOutEdge(); edge != null; edge = edge.getNextOut()) {
      SpaceEffGraphNode succ = edge.toNode();
      if (!succ.dfsVisited()) {
        succ.setDfsVisited();
        succ.setOnStack();
        dfs(succ);
      } else if (succ.onStack() || succ == node) {
        edge.setBackEdge();
      }
    }
    node.clearOnStack();
    for (SpaceEffGraphEdge edge = node.firstOutEdge(); edge != null; edge = edge.getNextOut()) {
      SpaceEffGraphNode succ = edge.toNode();
      if (!succ.topVisited() && !edge.backEdge()) {
        addTopSortNode(succ);
        succ.setTopVisited();
      }
    }
  }

  /**
   * Return a string representation of this graph.
   * @return a string representation of the graph
   */
  @Override
  public String toString() {
    StringBuilder res = new StringBuilder();
    for (SpaceEffGraphNode n = firstNode(); n != null; n = n.getNext()) {
      HashSet<SpaceEffGraphEdge> visitedNodes = new HashSet<SpaceEffGraphEdge>();
      int duplicatedNodes = 0;
      res.append("\nNode: ").append(n).append("\n");
      res.append("In nodes:\n");
      for (SpaceEffGraphEdge inEdge = n.firstInEdge(); inEdge != null; inEdge = inEdge.getNextIn()) {
        if (visitedNodes.contains(inEdge)) {
          duplicatedNodes ++;
          res.append("(Duplicated edge " + inEdge.toNodeString() + ")");
          if (duplicatedNodes > 5) {
            break;
          }
        } else {
          visitedNodes.add(inEdge);
          res.append(inEdge.getTypeString());
          res.append(" ");
          res.append(inEdge.fromNode());
          res.append("\n");
        }
      }
      res.append("\n");
      visitedNodes.clear();
      duplicatedNodes=0;
      res.append("Out nodes:\n");
      for (SpaceEffGraphEdge out = n.firstOutEdge(); out != null; out = out.getNextOut()) {
        if (visitedNodes.contains(out)) {
          duplicatedNodes ++;
          res.append("(Duplicated edge " + out.toNodeString() + ")");
          if (duplicatedNodes > 5) {
            break;
          }
        } else {
          res.append(out.getTypeString());
          res.append(" ");
          res.append(out.toNode());
          res.append("\n");
        }
      }
      if (res.length() > 50000) {
        res.append("....(giving up too long)\n");
        break;
      }
    }
    return res.toString();
  }

  ////////////////////
  // Return a breadth-first enumeration of the nodes in this CFG.
  // Note that this is different than topological ordering.
  // TODO: figure out how this works and add comments (IP)
  ////////////////////
  int markNumber;

  final int getNewMark() {
    return ++markNumber;
  }

  /**
   * Print, to System.out, the basic blocks in depth first order.
   */
  public void printDepthFirst() {
    markNumber = getNewMark();
    print(new DepthFirstEnumerator(_firstNode, markNumber));
  }

  /**
   * Print, to System.out, the basic blocks in the order given in
   * the supplied enumeration.
   * @param e enumeration order to print blocks
   */
  private void print(Enumeration<GraphNode> e) {
    while (e.hasMoreElements()) {
      SpaceEffGraphNode bb = (SpaceEffGraphNode) e.nextElement();
      bb.printExtended();
    }
  }

  private static final class NodeEnumeration implements GraphNodeEnumeration {
    private SpaceEffGraphNode _node;

    public NodeEnumeration(SpaceEffGraphNode n) { _node = n; }

    @Override
    public boolean hasMoreElements() { return _node != null; }

    @Override
    public GraphNode nextElement() { return next(); }

    @Override
    public GraphNode next() {
      SpaceEffGraphNode n = _node;
      _node = n.getNext();
      return n;
    }
  }
}

