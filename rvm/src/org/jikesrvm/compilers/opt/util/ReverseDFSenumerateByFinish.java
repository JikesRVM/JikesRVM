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
 *  This class generates an enumeration of nodes of a graph, in order
 * of increasing finishing time in a reverse Depth First Search,
 * i.e. a search traversing nodes from target to source.
 */
public class ReverseDFSenumerateByFinish extends DFSenumerateByFinish {

  /**
   *  Construct a reverse DFS across a graph.
   *
   * @param net The graph over which to search.
   */
  public ReverseDFSenumerateByFinish(Graph net) {
    super(net);
  }

  /**
   *  Construct a reverse DFS across a subset of a graph, starting
   * at the given set of nodes.
   *
   * @param net The graph over which to search
   * @param nodes The nodes at which to start the search
   */
  public ReverseDFSenumerateByFinish(Graph net, Enumeration<GraphNode> nodes) {
    super(net, nodes);
  }

  /**
   *  Traverse edges from target to source.
   *
   * @param n A node in the DFS
   * @return The nodes that have edges leading to n
   */
  @Override
  protected Enumeration<GraphNode> getConnected(GraphNode n) {
    return n.inNodes();
  }
}



