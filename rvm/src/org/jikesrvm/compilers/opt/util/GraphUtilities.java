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
 * This class implements miscellaneous utilities for graphs.
 */
public class GraphUtilities {

  /**
   * Return an enumeration of the nodes, or a subset of the nodes, in an
   * acyclic graph in topological order. <p>
   *
   * Note: if G is cyclic, results are undefined
   */
  public static Enumeration<GraphNode> enumerateTopSort(Graph G) {
    return enumerateTopSort(G, G.enumerateNodes());
  }

  public static Enumeration<GraphNode> enumerateTopSort(Graph G, Enumeration<GraphNode> ie) {
    return enumerateTopSortInternal(G, new DFSenumerateByFinish(G, ie));
  }

  public static Enumeration<GraphNode> enumerateTopSort(Graph G, Enumeration<GraphNode> ie,
                                                          GraphEdgeFilter f) {
    return enumerateTopSortInternal(G, new FilteredDFSenumerateByFinish(G, ie, f));
  }

  private static Enumeration<GraphNode> enumerateTopSortInternal(Graph G, Enumeration<GraphNode> e) {
    final GraphNode[] elts = new GraphNode[G.numberOfNodes()];

    int i = 0;
    while (e.hasMoreElements()) {
      elts[i++] = e.nextElement();
    }

    final int i1 = i;

    return new GraphNodeEnumerator() {
      private int top = i1;

      @Override
      public boolean hasMoreElements() {
        return top > 0;
      }

      @Override
      public GraphNode nextElement() {
        return elts[--top];
      }
    };
  }
}
