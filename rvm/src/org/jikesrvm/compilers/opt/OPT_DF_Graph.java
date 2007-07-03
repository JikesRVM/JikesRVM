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

import java.util.ArrayList;

/**
 * Implementation of a graph used in the guts of the dataflow equation
 * solver.
 */
class OPT_DF_Graph implements OPT_Graph {

  /**
   * The nodes of the graph.
   */
  public final ArrayList<OPT_GraphNode> nodes = new ArrayList<OPT_GraphNode>();

  /**
   * Number of nodes in the graph.
   */
  private int count = 0;

  /**
   * @return number of nodes in the graph
   */
  public int numberOfNodes() {
    return count;
  }

  /**
   * Implementation for OPT_Graph Interface.  TODO: why is this in the
   * OPT_Graph interface?
   */
  public void compactNodeNumbering() {}

  /**
   * Enumerate the nodes in the graph.
   * @return an enumeration of the nodes in the graph
   */
  public OPT_GraphNodeEnumeration enumerateNodes() {
    return new OPT_GraphNodeEnumeration() {
      private int i = 0;

      public boolean hasMoreElements() {
        return i < count;
      }

      public OPT_GraphNode next() {
        return nodes.get(i++);
      }

      public OPT_GraphNode nextElement() {
        return next();
      }
    };
  }

  /**
   * Add a node to the graph.
   * @param x the node to add
   */
  public void addGraphNode(OPT_GraphNode x) {
    x.setIndex(count);
    nodes.add(x);
    count++;
  }

  /**
   * Unsupported.  Why is this here?
   */
  public void addGraphEdge(OPT_GraphNode x, OPT_GraphNode y) {
    throw new OPT_OptimizingCompilerException("DF_Graph edges implicit");
  }
}

