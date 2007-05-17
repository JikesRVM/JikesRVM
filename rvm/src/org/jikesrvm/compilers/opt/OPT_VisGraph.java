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
 * OPT_VisGraph provides the minimum set of routines for graph
 * visualization.  The graph nodes and edges should implement OPT_VisNode
 * and OPT_VisEdge interfaces respectively.
 *
 * @see OPT_VisNode
 * @see OPT_VisEdge
 */

public interface OPT_VisGraph {
  /**
   * Returns the nodes of the graph.
   * Each of the nodes has to implement the OPT_VisNode interface
   * @return the enumeration that would list the nodes of the graph
   */
  Enumeration<OPT_VCGNode> nodes();
}

