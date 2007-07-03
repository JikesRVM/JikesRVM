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
 * This class implements miscellaneous utilities for graphs.
 */
class OPT_GraphUtilities {

  /**
   * Return an enumeration of the nodes, or a subset of the nodes, in an
   * acyclic graph in topological order .
   *
   * Note: if G is cyclic, results are undefined
   */
  public static OPT_GraphNodeEnumeration enumerateTopSort(OPT_Graph G) {
    return enumerateTopSort(G, G.enumerateNodes());
  }

  public static OPT_GraphNodeEnumeration enumerateTopSort(OPT_Graph G, OPT_GraphNodeEnumeration ie) {
    return enumerateTopSortInternal(G, new OPT_DFSenumerateByFinish(G, ie));
  }

  public static OPT_GraphNodeEnumeration enumerateTopSort(OPT_Graph G, OPT_GraphNodeEnumeration ie,
                                                          OPT_GraphEdgeFilter f) {
    return enumerateTopSortInternal(G, new OPT_FilteredDFSenumerateByFinish(G, ie, f));
  }

  private static OPT_GraphNodeEnumeration enumerateTopSortInternal(OPT_Graph G, OPT_GraphNodeEnumeration e) {
    final OPT_GraphNode[] elts = new OPT_GraphNode[G.numberOfNodes()];

    int i = 0;
    while (e.hasMoreElements()) {
      elts[i++] = e.next();
    }

    final int i1 = i;

    return new OPT_GraphNodeEnumerator() {
      private int top = i1;

      public boolean hasMoreElements() {
        return top > 0;
      }

      public OPT_GraphNode next() {
        return elts[--top];
      }
    };
  }
}
