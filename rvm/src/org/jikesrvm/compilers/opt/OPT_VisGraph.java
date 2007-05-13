/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//OPT_VisGraph.java
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

