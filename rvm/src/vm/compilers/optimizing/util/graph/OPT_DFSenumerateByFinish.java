/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.*;

/**
 * This class implements depth-first search over a OPT_Graph,
 * return an enumeration of the nodes of the graph in order of
 * increasing finishing time.  This class follows the outNodes of the
 * graph nodes to define the graph, but this behavior can be changed
 * by overriding the getConnected method.
 *
 * @author Julian Dolby
 */
class OPT_DFSenumerateByFinish extends OPT_Stack
    implements OPT_GraphNodeEnumeration {

  /**
   *  Construct a depth-first enumerator across all the nodes of a
   * graph. 
   *
   * @param net the graph whose nodes to enumerate
   */
  OPT_DFSenumerateByFinish (OPT_Graph net) {
    this(net, net.enumerateNodes());
  }

  /**
   * Construct a depth-first enumerator across the (possibly
   * improper) subset of nodes reachable from the nodes in the given
   * enumeration. 
   *
   * @param net the graph whose nodes to enumerate
   * @param nodes the set of nodes from which to start searching
   */
  OPT_DFSenumerateByFinish (OPT_Graph net, OPT_GraphNodeEnumeration nodes) {
    e = nodes;
    net.compactNodeNumbering();
    info = new OPT_GraphNodeEnumeration[net.numberOfNodes() + 1];
    //  info = new java.util.HashMap( net.numberOfNodes() );
    if (e.hasMoreElements())
      theNextElement = e.next();
  }
  /**
   * While a depth-first enumeration is in progress, this field
   * holds the current root node, i.e. the current botton of the
   * search stack (assuming stacks grow upward).  This is used
   * primarily when constructing strongly connected components. 
   */
  public OPT_GraphNode currentRoot;

  /**
   * Return whether there are any more nodes left to enumerate.
   *
   * @return true if there nodes left to enumerate.
   */
  public boolean hasMoreElements () {
    return  (!empty() || (theNextElement != null && info[theNextElement.getIndex()]
        == null));
  }

  /**
   *  Find the next graph node in finishing time order.
   *
   *  @see #nextElement
   *
   *  @return the next graph node in finishing time order.
   */
  public OPT_GraphNode next () {
    if (empty()) {
      OPT_GraphNode v = theNextElement;
      currentRoot = theNextElement;
      info[v.getIndex()] = getConnected(v);
      push(v);
    }
    recurse: while (!empty()) {
      OPT_GraphNode v = (OPT_GraphNode)peek();
      OPT_GraphNodeEnumeration pendingChildren = info[v.getIndex()];
      for (OPT_GraphNodeEnumeration e = pendingChildren; e.hasMoreElements();) {
        OPT_GraphNode n = e.next();
        OPT_GraphNodeEnumeration nChildren = info[n.getIndex()];
        if (nChildren == null) {
          // found a new child: recurse to it.
          info[n.getIndex()] = getConnected(n);
          push(n);
          continue  recurse;
        }
      }
      // no more children to visit: finished this vertex
      while (info[theNextElement.getIndex()] != null && e.hasMoreElements())
        theNextElement = e.next();
      return  (OPT_GraphNode)pop();
    }
    return  null;
  }

  /**
   *  Wrapper for next() to make the Enumeration interface happy
   *
   * @see #next
   *
   * @return the next node in finishing time order
   *
   */
  public Object nextElement () {
    return  next();
  }
  /**
   * the current next element in finishing time order
   */
  private OPT_GraphNode theNextElement;
  /**
   * an enumeration of all nodes to search from
   */
  private OPT_GraphNodeEnumeration e;
  /**
   * an enumeration of child nodes for each node being searched
   */
  private OPT_GraphNodeEnumeration[] info;

  /**
   * get the out edges of a given node
   *
   * @param n the node of which to get the out edges
   * @return the out edges
   *
   */
  protected OPT_GraphNodeEnumeration getConnected(OPT_GraphNode n) {
    return  n.outNodes();
  }
}
