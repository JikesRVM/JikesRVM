/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

/**
 * An abstract interface for generic graphs; general graph utilities
 * should be defined in terms of this interface and all graph
 * implementations in the system should implement it.
 *
 *
 * @see OPT_GraphNode
 * @see OPT_GraphEdge
 * @see OPT_GraphUtilities
 */
interface OPT_Graph {

  /**
   * This method lists all of the nodes in a given graph.  This is
   * defined in terms of generic OPT_GraphNodes.
   *
   * @see OPT_GraphNode
   *
   * @return an enumeration of all nodes in the graph
   */
  OPT_GraphNodeEnumeration enumerateNodes();

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
   * {@link OPT_GraphNode#getIndex OPT_GraphNode.getIndex}.  This
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
  void addGraphNode(OPT_GraphNode node);

  /**
   *  Add a new edge to a graph.  This method is deliberately
   * defined in terms of nodes to avoid requiring graphs that
   * implement OPT_Graph have explicit edge objects.
   *
   * @param source the source node of the edge to add
   * @param target the target node of the edge to add
   */
  void addGraphEdge(OPT_GraphNode source, OPT_GraphNode target);
}
