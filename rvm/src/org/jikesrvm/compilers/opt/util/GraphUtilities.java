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


/**
 * This class implements miscellaneous utilities for graphs.
 */
public class GraphUtilities {

  /**
   * Return an enumeration of the nodes, or a subset of the nodes, in an
   * acyclic graph in topological order .
   *
   * Note: if G is cyclic, results are undefined
   */
  public static GraphNodeEnumeration enumerateTopSort(Graph G) {
    return enumerateTopSort(G, G.enumerateNodes());
  }

  public static GraphNodeEnumeration enumerateTopSort(Graph G, GraphNodeEnumeration ie) {
    return enumerateTopSortInternal(G, new DFSenumerateByFinish(G, ie));
  }

  public static GraphNodeEnumeration enumerateTopSort(Graph G, GraphNodeEnumeration ie,
                                                          GraphEdgeFilter f) {
    return enumerateTopSortInternal(G, new FilteredDFSenumerateByFinish(G, ie, f));
  }

  private static GraphNodeEnumeration enumerateTopSortInternal(Graph G, GraphNodeEnumeration e) {
    final GraphNode[] elts = new GraphNode[G.numberOfNodes()];

    int i = 0;
    while (e.hasMoreElements()) {
      elts[i++] = e.next();
    }

    final int i1 = i;

    return new GraphNodeEnumerator() {
      private int top = i1;

      public boolean hasMoreElements() {
        return top > 0;
      }

      public GraphNode next() {
        return elts[--top];
      }
    };
  }
}
