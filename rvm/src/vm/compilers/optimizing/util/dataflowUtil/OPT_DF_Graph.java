/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Vector;

/**
 * Implementation of a graph used in the guts of the dataflow equation
 * solver.
 *
 * @author Stephen Fink
 */
class OPT_DF_Graph
    implements OPT_Graph {
  /**
   * The nodes of the graph.
   */
  private Vector nodes = new Vector();
  /**
   * Number of nodes in the graph.
   */
  private int count = 0;

  /**
   * Get the number of nodes in the graph.
   * @return number of nodes in the graph
   */
  public int numberOfNodes () {
    return  count;
  }

  /**
   * Implementation for OPT_Graph Interface.  TODO: why is this in the
   * OPT_Graph interface?
   */
  public void compactNodeNumbering () {}

  /**
   * Enumerate the nodes in the graph.
   * @return the nodes in the graph
   */
  public OPT_GraphNodeEnumeration enumerateNodes () {
    return  new OPT_GraphNodeEnumeration() {
      private int i = 0;

      public boolean hasMoreElements () {
        return  i < count;
      }

      public OPT_GraphNode next () {
        return  (OPT_GraphNode)nodes.elementAt(i++);
      }

      public Object nextElement () {
        return  next();
      }
    };
  }

  /**
   * Add a node to the graph.
   * @param x the node to add
   */
  public void addGraphNode (OPT_GraphNode x) {
    x.setIndex(count);
    nodes.addElement(x);
    count++;
  }

  /**
   * Unsupported.  Why is this here?
   */
  public void addGraphEdge (OPT_GraphNode x, OPT_GraphNode y) {
    throw  new OPT_OptimizingCompilerException("DF_Graph edges implicit");
  }
}

