/*
 * (C) Copyright IBM Corp. 2001
 */

import  java.util.*;


/**
 *  This class implements a graph vertex that holds a
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
 *
 */
class OPT_SCC extends OPT_EdgelessGraphNode {
  /**
   *  A linked list of graph nodes in this SCC
   */
  private OPT_LinkedListObjectElement nodes = null;

  /**
   *  Add a given graph node to this SCC
   *
   * @param n the node to add
   */
  public void add (OPT_GraphNode n) {
    nodes = new OPT_LinkedListObjectElement(n, nodes);
  }

  /**
   *  Generate a human-readable representation of this SCC
   * @return a human-readable representation of this SCC
   */
  public String toString () {
    return  "SCC: " + nodes.toString();
  }

  /**
   *  Enumerate all the nodes contained in this SCC
   * @return an enumeration of all the nodes contained in this SCC
   */
  public OPT_GraphNodeEnumeration enumerateVertices () {
    final Enumeration e = new OPT_LinkedListObjectEnumerator(nodes);
    return  new OPT_GraphNodeEnumeration() {

      /**
       * put your documentation comment here
       * @return 
       */
      public boolean hasMoreElements () {
        return  e.hasMoreElements();
      }

      /**
       * put your documentation comment here
       * @return 
       */
      public OPT_GraphNode next () {
        return  (OPT_GraphNode)e.nextElement();
      }

      /**
       * put your documentation comment here
       * @return 
       */
      public Object nextElement () {
        return  next();
      }
    };
  }
}



