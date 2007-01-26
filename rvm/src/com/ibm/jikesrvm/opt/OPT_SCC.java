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
 * This class implements a graph vertex that holds a
 * Strongly-Connected Component (SCC).  It co-operates with
 * OPT_SCC_Graph to implement a graph of SCCs, but it can also be
 * used independently to represent a free-standing  SCC.  Such
 * free-standing SCCs can be created using an OPT_SCC_Enumeration,
 * and those SCCs turned into a graph using the constructor of
 * OPT_SCC_Graph. 
 *
 * @author Julian Dolby 
 *
 * @see OPT_SCC_Graph
 * @see OPT_SCC_Enumeration
 */
class OPT_SCC extends OPT_EdgelessGraphNode {
  /**
   *  A linked list of graph nodes in this SCC
   */
  private List<OPT_GraphNode> nodes = new LinkedList<OPT_GraphNode>();

  /**
   * Add a given graph node to this SCC
   *
   * @param n the node to add
   */
  public void add(OPT_GraphNode n) {
    nodes.add(n);
  }

  /**
   * Generate a human-readable representation of this SCC
   * @return a human-readable representation of this SCC
   */
  public String toString() {
    return  "SCC: " + nodes.toString();
  }

  /**
   * Enumerate all the nodes contained in this SCC
   * @return an enumeration of all the nodes contained in this SCC
   */
  public OPT_GraphNodeEnumeration enumerateVertices() {
    final Iterator<OPT_GraphNode> e = nodes.iterator();
    return  new OPT_GraphNodeEnumeration() {

      public boolean hasMoreElements() {
        return e.hasNext();
      }

      public OPT_GraphNode next() {
        return e.next();
      }

      public OPT_GraphNode nextElement() {
        return e.next();
      }
    };
  }
  private final static long serialVersionUID = 1L;
}



