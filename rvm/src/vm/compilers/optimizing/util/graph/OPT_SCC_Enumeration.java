/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.util.*;

/**
 * This class computes strongly connected components for an OPT_Graph
 * (or a subset of it). It does not store the SCCs in any lookaside
 * structure, but rather simply generates an enumeration of them.
 * This enumeration is used to create graphs of SCCs by
 * OPT_SCC_Graph. 
 *
 * See Cormen, Leiserson, Rivest Ch. 23 Sec. 5 
 *
 * @author Julian Dolby
 *
 * @see OPT_SCC_Graph
 * @see OPT_SCC
 *
 */
class OPT_SCC_Enumeration
    implements Enumeration {
  /**
   *  The second DFS (the reverse one) needed while computing SCCs
   */
  private OPT_DFSenumerateByFinish rev;

  /**
   *  Construct an enumeration across the SCCs of a given graph.
   *
   * @param net The graph over which to construct SCCs
   */
  OPT_SCC_Enumeration (OPT_Graph net) {
    this(net, net.enumerateNodes());
  }

  /**
   *  Construct an enumeration of the SCCs of the subset of a given
   * graph determined by starting at a given set of nodes.
   *
   * @param net The graph over which to compute SCCs.
   * @param e The set of nodes that determine the subset of interest
   */
  OPT_SCC_Enumeration (OPT_Graph net, OPT_GraphNodeEnumeration e) {
    OPT_GraphNodeEnumeration topOrder = OPT_GraphUtilities.enumerateTopSort(net, 
        e);
    rev = new OPT_ReverseDFSenumerateByFinish(net, topOrder);
  }

  /**
   *  Construct an enumeration of the SCCs of the subset of a given
   * graph determined by starting at a given set of nodes.
   *
   * @param net The graph over which to compute SCCs.
   * @param e The set of nodes that determine the subset of interest
   */
  OPT_SCC_Enumeration (OPT_Graph net, OPT_GraphNodeEnumeration e, OPT_GraphEdgeFilter filter) {
    OPT_GraphNodeEnumeration topOrder = OPT_GraphUtilities.enumerateTopSort(net, e, filter);
    rev = new OPT_ReverseFilteredDFSenumerateByFinish(net, topOrder, filter);
  }

  /**
   *  Determine whether there are any more SCCs remaining in this
   * enumeration. 
   *
   * @return True if there are any more SCCs remaining in this
   * enumeration. 
   */
  public boolean hasMoreElements () {
    return  rev.hasMoreElements();
  }

  /**
   *  Find the next SCC in this enumeration
   *
   * @return The next SCC in this enumeration
   */
  public OPT_SCC next () {
    OPT_GraphNode v = (OPT_GraphNode)rev.nextElement();
    OPT_SCC currentSCC = new OPT_SCC();
    currentSCC.add(v);
    while (rev.hasMoreElements() && rev.currentRoot != v) {
      v = (OPT_GraphNode)rev.nextElement();
      currentSCC.add(v);
    }
    return  currentSCC;
  }

  /**
   *  Find the next SCC in this enumeration
   *
   * @return The next SCC in this enumeration
   */
  public Object nextElement () {
    return  next();
  }
}



