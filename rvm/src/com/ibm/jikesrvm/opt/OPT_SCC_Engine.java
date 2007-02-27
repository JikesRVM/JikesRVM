/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

import java.util.HashMap;
import java.util.HashSet;

/**
 * This class computes strongly connected components for a OPT_Graph
 * See Cormen, Leiserson, Rivest Ch. 23 Sec. 5
 *
 * @author Stephen Fink
 * @modified Julian Dolby
 */
class OPT_SCC_Engine {

  /**
   *  Compue the SCCs of a given graph.
   *
   * @param net The graph over which to compute SCCs
   */
  OPT_SCC_Engine (OPT_Graph net) {
    computeSCCs(net);
  }

  /**
   *  Enumerate all of the SCCs computed.
   *
   * @return An enumeration of all SCCs
   */
  java.util.Iterator<OPT_SCC> iterateSCCs () {
    return  sccs.iterator();
  }

  /**
   *  Find the SCC for a given graph node.
   *
   * @param v The graph whose SCC is to be found.
   * @return The SCC for v.
   */
  OPT_SCC getSCC (OPT_GraphNode v) {
    return  vertexHash.get(v);
  }
  /**
   *  Map of graph nodes to SCCs
   */
  private HashMap<OPT_GraphNode,OPT_SCC> vertexHash = 
    new HashMap<OPT_GraphNode,OPT_SCC>();
  /**
   *  Set of all SCCs
   */
  private HashSet<OPT_SCC> sccs = new HashSet<OPT_SCC>();

  /** 
   * Compute the SCCs and cache the result
   *
   * @param net The graph over which to compute SCCs
   */
  private void computeSCCs (OPT_Graph net) {
    OPT_GraphNodeEnumeration topOrder = OPT_GraphUtilities.enumerateTopSort(net);
    OPT_DFSenumerateByFinish rev = new OPT_ReverseDFSenumerateByFinish(net, 
        topOrder);
    OPT_GraphNode currentRoot = null;
    OPT_SCC currentSCC = null;
    while (rev.hasMoreElements()) {
      OPT_GraphNode v = rev.nextElement();
      if (rev.currentRoot != currentRoot) {
        currentRoot = rev.currentRoot;
        currentSCC = new OPT_SCC();
        sccs.add(currentSCC);
      }
      currentSCC.add(v);
      vertexHash.put(v, currentSCC);
    }
  }
}



