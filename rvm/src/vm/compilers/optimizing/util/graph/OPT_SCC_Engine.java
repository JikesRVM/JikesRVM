/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.util.*;

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
  java.util.Iterator iterateSCCs () {
    return  sccs.iterator();
  }

  /**
   *  Find the SCC for a given graph node.
   *
   * @param v The graph whose SCC is to be found.
   * @return The SCC for v.
   */
  OPT_SCC getSCC (OPT_GraphNode v) {
    return  (OPT_SCC)vertexHash.get(v);
  }
  /**
   *  Map of graph nodes to SCCs
   */
  private java.util.HashMap vertexHash = new java.util.HashMap();
  /**
   *  Set of all SCCs
   */
  private java.util.HashSet sccs = new java.util.HashSet();

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
      OPT_GraphNode v = (OPT_GraphNode)rev.nextElement();
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



