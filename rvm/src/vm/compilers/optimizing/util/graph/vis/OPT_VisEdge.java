/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//OPT_VisEdge.java
//$Id$
package com.ibm.JikesRVM.opt;

import java.util.Enumeration;

/**
 * OPT_VisEdge provides the minimum set of routines for graph edge
 * visualization.  The graph should implement OPT_VisGraph interface, and
 * its nodes - OPT_VisNode interface.
 *
 * @author Igor Pechtchanski
 * @see OPT_VisGraph
 * @see OPT_VisNode
 */

public interface OPT_VisEdge {
  /**
   * Returns the source node of the edge.
   * The node has to implement the OPT_VisNode interface
   * @return edge source node
   */
  public OPT_VisNode sourceNode();

  /**
   * Returns the target node of the edge.
   * The node has to implement the OPT_VisNode interface
   * @return edge target node
   */
  public OPT_VisNode targetNode();
}


