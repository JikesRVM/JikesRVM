/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.ArrayList;

/**
 * Implementation of a graph used in the guts of the dataflow equation
 * solver.
 *
 * @author Stephen Fink
 */
class OPT_DF_Graph implements OPT_Graph {

  /**
   * The nodes of the graph.
   */
  public ArrayList nodes = new ArrayList();

  /**
   * Number of nodes in the graph.
   */
  private int count = 0;

  /**
   * @return number of nodes in the graph
   */
  public int numberOfNodes () {
    return count;
  }

  /**
   * Implementation for OPT_Graph Interface.  TODO: why is this in the
   * OPT_Graph interface?
   */
  public void compactNodeNumbering () {}

  /**
   * Enumerate the nodes in the graph.
   * @return an enumeration of the nodes in the graph
   */
  public OPT_GraphNodeEnumeration enumerateNodes () {
    return new OPT_GraphNodeEnumeration() {
      private int i = 0;

      public boolean hasMoreElements () {
        return i < count;
      }

      public OPT_GraphNode next () {
        return (OPT_GraphNode)nodes.get(i++);
      }

      public Object nextElement () {
        return next();
      }
    };
  }

  /**
   * Add a node to the graph.
   * @param x the node to add
   */
  public void addGraphNode (OPT_GraphNode x) {
    x.setIndex(count);
    nodes.add(x);
    count++;
  }

  /**
   * Unsupported.  Why is this here?
   */
  public void addGraphEdge (OPT_GraphNode x, OPT_GraphNode y) {
    throw new OPT_OptimizingCompilerException("DF_Graph edges implicit");
  }
}

