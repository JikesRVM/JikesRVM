/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import  java.util.*;

/**
 * This class implements a graph where each node is a strongly
 * connected component of OPT_GraphNodes
 *
 * @author Stephen Fink
 * @modified Julian Dolby
 *
 */
class OPT_SCC_Graph extends OPT_EdgelessGraph {

  /**
   * Create the OPT_SCC_Graph corresponding to a OPT_Graph
   *
   * @param G The graph for which to build the corresponding SCC graph.
   *
   */
  OPT_SCC_Graph (OPT_Graph G) {
    // compute SCCs
    OPT_SCC_Enumeration sccs = new OPT_SCC_Enumeration(G);
    while (sccs.hasMoreElements()) {
      OPT_SCC scc = sccs.next();
      addGraphNode(scc);
      OPT_GraphNodeEnumeration e = scc.enumerateVertices();
      while (e.hasMoreElements())
        e.next().setScratchObject(scc);
    }
    // add edges between graph vertices
    for (OPT_GraphNodeEnumeration e = G.enumerateNodes(); e.hasMoreElements();) {
      OPT_GraphNode v1 = e.next();
      OPT_SCC s1 = (OPT_SCC)v1.getScratchObject();
      for (OPT_GraphNodeEnumeration e1 = v1.outNodes(); e1.hasMoreElements();) {
        OPT_GraphNode v2 = e1.next();
        OPT_SCC s2 = (OPT_SCC)v2.getScratchObject();
        if (! s1.hasOut(s2))
          addGraphEdge(s1, s2);
      }
    }
  }
}



