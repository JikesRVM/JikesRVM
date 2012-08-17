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
 * An abstract interface for generic graphs; general graph utilities
 * should be defined in terms of this interface and all graph
 * implementations in the system should implement it.
 *
 *
 * @see GraphNode
 * @see GraphEdge
 * @see GraphUtilities
 */
public interface Graph {

  /**
   * This method lists all of the nodes in a given graph.  This is
   * defined in terms of generic GraphNodes.
   *
   * @see GraphNode
   *
   * @return an enumeration of all nodes in the graph
   */
  Enumeration<GraphNode> enumerateNodes();

  /**
   *  Find out how many nodes are in the graph
   *
   *  @return the number of nodes in the graph
   */
  int numberOfNodes();

  /**
   *  After this method is called, all nodes in the graph should
   * have a compact numbering from 0 to (number of nodes in
   * graph - 1).  This number is what should be returned by
   * {@link GraphNode#getIndex GraphNode.getIndex}.  This
   * method is used by clients that want to e.g. allocate look-aside
   * storage for graph nodes in an
   * array.
   */
  void compactNodeNumbering();

  /**
   *  Add a new graph node to the graph.
   *
   * @param node the node to add to the graph
   */
  void addGraphNode(GraphNode node);

  /**
   *  Add a new edge to a graph.  This method is deliberately
   * defined in terms of nodes to avoid requiring graphs that
   * implement Graph have explicit edge objects.
   *
   * @param source the source node of the edge to add
   * @param target the target node of the edge to add
   */
  void addGraphEdge(GraphNode source, GraphNode target);
}
