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
 * VisGraph provides the minimum set of routines for graph
 * visualization.  The graph nodes and edges should implement VisNode
 * and VisEdge interfaces respectively.
 *
 * @see VisNode
 * @see VisEdge
 */

public interface VisGraph {
  /**
   * Returns the nodes of the graph.
   * Each of the nodes has to implement the VisNode interface
   * @return the enumeration that would list the nodes of the graph
   */
  Enumeration<VCGNode> nodes();
}

