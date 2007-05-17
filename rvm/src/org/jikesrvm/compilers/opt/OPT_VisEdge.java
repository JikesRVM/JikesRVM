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
 * OPT_VisEdge provides the minimum set of routines for graph edge
 * visualization.  The graph should implement OPT_VisGraph interface, and
 * its nodes - OPT_VisNode interface.
 *
 * @see OPT_VisGraph
 * @see OPT_VisNode
 */

public interface OPT_VisEdge {
  /**
   * Returns the source node of the edge.
   * The node has to implement the OPT_VisNode interface
   * @return edge source node
   */
  OPT_VisNode sourceNode();

  /**
   * Returns the target node of the edge.
   * The node has to implement the OPT_VisNode interface
   * @return edge target node
   */
  OPT_VisNode targetNode();
}


