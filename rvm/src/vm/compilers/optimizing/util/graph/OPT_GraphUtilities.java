/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;

/**
 * This class implements miscellaneous utilities for graphs.
 *
 * @author Stephen Fink
 */
class OPT_GraphUtilities {

  /**
   * Return an enumeration of the nodes, or a subset of the nodes, in an 
   * acyclic graph in topological order .
   *
   * Note: if G is cyclic, results are undefined
   */
  public static OPT_GraphNodeEnumeration enumerateTopSort(OPT_Graph G) {
    return  enumerateTopSort(G, G.enumerateNodes());
  }

  public static OPT_GraphNodeEnumeration enumerateTopSort(OPT_Graph G, OPT_GraphNodeEnumeration ie) {
    return enumerateTopSortInternal(G, new OPT_DFSenumerateByFinish(G, ie));
  }

  public static OPT_GraphNodeEnumeration enumerateTopSort(OPT_Graph G, OPT_GraphNodeEnumeration ie, 
                                                          OPT_GraphEdgeFilter f) {
      return enumerateTopSortInternal(G, new OPT_FilteredDFSenumerateByFinish(G, ie, f));
    }

  public static OPT_GraphNodeEnumeration enumerateTopSortInternal(OPT_Graph G, 
                                                                  OPT_GraphNodeEnumeration e) { 
      final OPT_GraphNode[] elts = new OPT_GraphNode[ G.numberOfNodes() ];

      int i = 0;
      while (e.hasMoreElements())
        elts[i++] = e.next();

      final int i1 = i;

      return  new OPT_GraphNodeEnumeration() {
        private int top = i1;

        public boolean hasMoreElements() {
          return top > 0;
        }

        public OPT_GraphNode next() {
          return elts[--top];
        }

        public Object nextElement() {
          return next();
        }
      };
    }

  /**
   * Sort the nodes in a graph by decreasing DFS finish time
   */
  public static SortedSet sortByDecreasingFinishTime(OPT_Graph net, OPT_DFS dfs) {
    FinishTimeComparator f = new FinishTimeComparator(dfs);
    TreeSet result = new TreeSet(f);
    for(Enumeration e = net.enumerateNodes(); e.hasMoreElements();) {
      OPT_GraphNode v =(OPT_GraphNode)e.nextElement();
      result.add(v);
    }
    return  result;
  }

  static class FinishTimeComparator implements Comparator {
      private OPT_DFS dfs;

      FinishTimeComparator(OPT_DFS dfs) {
        this.dfs = dfs;
      }

      public int compare(Object o1, Object o2) {
        OPT_GraphNode v1 = (OPT_GraphNode)o1;
        OPT_GraphNode v2 = (OPT_GraphNode)o2;
        int f1 = dfs.getFinish(v1);
        int f2 = dfs.getFinish(v2);
        return  f2 - f1;
      }
    }

  /**
   * Checks if sorted defines a topological order of graph nodes
   *
   * @param sorted the ordered list of elements of the graph
   * @param forward true if all the edges of the nodes in sorted should 
   * point toward nodes at a higher index in sorted, false if all the edges
   * should point toward nodes at a lower index in sorted
   *
   * @return true if sorted is a topological order.  (Note that will
   * always return false for a cyclic graph, since no such order is
   * possible). 
   *
   * Note: The equals() method must be defined properly for uniqueness of the
   * OPT_GraphNodes to be checked correctly.
   */
  public static boolean isTopologicalOrder(Enumeration sorted, boolean forward) {
    while (sorted.hasMoreElements()) {
      OPT_GraphNode cur = (OPT_GraphNode)sorted.nextElement();
      HashSet s = new HashSet();
      if (!s.add(cur))
        return  false;
      Enumeration e = cur.outNodes();
      while (e.hasMoreElements()) {
        OPT_GraphNode out = (OPT_GraphNode)e.nextElement();
        if (forward == s.contains(out))
          return  false;
      }
    }
    return  true;
  }

  /**
   *  Gets the nodes of a graph sorted in topological order. Returns null
   *  if a cycle is detected in the graph.
   *
   *  @param G the graph
   *
   *  @return the nodes sorted in topological order if the graph contains no 
   *  cycles, otherwise null
   *
   *  Note: Duplicated code in enumerateTopSort in order to avoid doing the 
   *  topological sort twice.
   */
  public static Enumeration enumerateTopSortWithCheck(OPT_Graph G) {
    OPT_Stack order = new OPT_Stack();
    OPT_GraphNodeEnumeration e = new OPT_DFSenumerateByFinish(G);
    while (e.hasMoreElements())
      order.push(e.nextElement());
    if (isTopologicalOrder(order.elements(), true)) {
      final Enumeration e1 = order.elements();
      return  new OPT_GraphNodeEnumeration() {

        public boolean hasMoreElements() {
          return  e1.hasMoreElements();
        }

        public OPT_GraphNode next() {
          return  (OPT_GraphNode)e1.nextElement();
        }

        public Object nextElement() {
          return  next();
        }
      };
    } 
    else 
      return  null;
  }
}
