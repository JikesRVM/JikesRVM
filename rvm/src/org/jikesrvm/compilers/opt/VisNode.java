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

import java.util.Enumeration;

/**
 * VisNode provides the minimum set of routines for graph node
 * visualization.  The graph should implement VisGraph interface, and
 * its edges - VisEdge interface.
 *
 * @see VisGraph
 * @see VisEdge
 */

public interface VisNode {
  /**
   * Returns the edges of the node.
   * Each of the edges has to implement the VisEdge interface
   * @return the enumeration that would list the edges of the node
   */
  Enumeration<VisEdge> edges();

  /**
   * To be used for implementing edges() for graphs that don't
   * have explicit edge representation.
   */
  class DefaultEdge implements VisEdge {
    private VisNode _s, _t;

    public DefaultEdge(VisNode s, VisNode t) {
      _s = s;
      _t = t;
    }

    public VisNode sourceNode() { return _s; }

    public VisNode targetNode() { return _t; }
  }
}

