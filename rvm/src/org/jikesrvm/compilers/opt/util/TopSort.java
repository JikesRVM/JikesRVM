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


/**
 * Depth First Spanning Tree
 * March 14, 1998
 * Builds topological sort of a graph consisting of SortedGraphNode.
 */
public final class TopSort extends Stack<SortedGraphNode> {

  /**
   * the "visited" marker to use
   */
  private int sortMarker;

  /**
   * the next "number" to give out
   */
  private int sortNumber;

  /**
   * the last node to get a number
   */
  private SortedGraphNode lastNumberedNode;

  /**
   * are we processing the graph in forward order?
   */
  private boolean forward;

  /**
   * Prevent instantiation
   */
  private TopSort() { }

  /**
   * @param graph the graph
   * @param forward should we treat edges as forward?
   *  This is the second version of the implementation
   *   (see CVS file for older one)
   */
  public static SortedGraphNode buildTopological(TopSortInterface graph, boolean forward) {

    SortedGraphNode start = graph.startNode(forward);
    TopSort sorter = new TopSort();
    sorter.sortMarker = SortedGraphNode.getNewSortMarker(start);
    sorter.forward = forward;
    sorter.DFS(start, graph.numberOfNodes());
    return sorter.lastNumberedNode;
  }

  /**
   * Depth-first numbering in a non-recursive manner
   * @param node the root node
   * @param numNodes the number of nodes in this graph
   */
  private void DFS(SortedGraphNode node, int numNodes) {

    // push node on to the emulated activation stack
    push(node);
    @SuppressWarnings("unchecked") // the java generic array problem
        Enumeration<? extends SortedGraphNode>[] nodeEnum = new Enumeration[numNodes];

    recurse:
    while (!empty()) {

      node = peek();

      // check if we were already processing this node, if so we would have
      // saved the state of the enumeration in the loop below
      Enumeration<? extends SortedGraphNode> _enum = nodeEnum[node.getNumber()];
      if (_enum == null) {
        // mark node as visited
        node.setSortMarker(sortMarker);
        if (forward) {
          _enum = node.getOutNodes();
        } else {
          _enum = node.getInNodes();
        }
      }

      while (_enum.hasMoreElements()) {
        SortedGraphNode target = _enum.nextElement();

        // have we visited target?
        if (target.getSortMarker() != sortMarker) {
          // simulate a recursive call
          // but first save the enumeration state for resumption later
          nodeEnum[node.getNumber()] = _enum;
          push(target);
          continue recurse;
        }
      }

      // give node the next smallest number
      node.setSortNumber(sortNumber--, forward);
      // link it to the previous smallest node, even if that node is null
      node.setSortedNext(lastNumberedNode, forward);
      // update the smallest node
      lastNumberedNode = node;

      // "Pop" from the emulated activiation stack
      pop();
    }
  }
}



