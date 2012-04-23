/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.util;


/**
 * This class implements depth-first search over a Graph,
 * return an enumeration of the nodes of the graph in order of
 * increasing finishing time.  This class follows the outNodes of the
 * graph nodes to define the graph, but this behavior can be changed
 * by overriding the getConnected method.
 */
public class DFSenumerateByFinish extends Stack<GraphNode> implements GraphNodeEnumeration {

  /**
   *  Construct a depth-first enumerator across all the nodes of a
   * graph.
   *
   * @param net the graph whose nodes to enumerate
   */
  protected DFSenumerateByFinish(Graph net) {
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
  public DFSenumerateByFinish(Graph net, GraphNodeEnumeration nodes) {
    e = nodes;
    net.compactNodeNumbering();
    info = new GraphNodeEnumeration[net.numberOfNodes() + 1];
    //  info = new java.util.HashMap( net.numberOfNodes() );
    if (e.hasMoreElements()) {
      theNextElement = e.next();
    }
  }

  /**
   * While a depth-first enumeration is in progress, this field
   * holds the current root node, i.e. the current botton of the
   * search stack (assuming stacks grow upward).  This is used
   * primarily when constructing strongly connected components.
   */
  public GraphNode currentRoot;

  /**
   * Return whether there are any more nodes left to enumerate.
   *
   * @return true if there nodes left to enumerate.
   */
  @Override
  public boolean hasMoreElements() {
    return (!empty() || (theNextElement != null && info[theNextElement.getIndex()] == null));
  }

  /**
   *  Find the next graph node in finishing time order.
   *
   *  @see #nextElement
   *
   *  @return the next graph node in finishing time order.
   */
  @Override
  public GraphNode next() {
    if (empty()) {
      GraphNode v = theNextElement;
      currentRoot = theNextElement;
      info[v.getIndex()] = getConnected(v);
      push(v);
    }
    recurse:
    while (!empty()) {
      GraphNode v = peek();
      GraphNodeEnumeration pendingChildren = info[v.getIndex()];
      for (GraphNodeEnumeration e = pendingChildren; e.hasMoreElements();) {
        GraphNode n = e.next();
        GraphNodeEnumeration nChildren = info[n.getIndex()];
        if (nChildren == null) {
          // found a new child: recurse to it.
          info[n.getIndex()] = getConnected(n);
          push(n);
          continue recurse;
        }
      }
      // no more children to visit: finished this vertex
      while (info[theNextElement.getIndex()] != null && e.hasMoreElements()) {
        theNextElement = e.next();
      }
      return pop();
    }
    return null;
  }

  /**
   *  Wrapper for next() to make the Enumeration interface happy
   *
   * @see #next
   *
   * @return the next node in finishing time order
   */
  @Override
  public GraphNode nextElement() {
    return next();
  }

  /**
   * the current next element in finishing time order
   */
  private GraphNode theNextElement;
  /**
   * an enumeration of all nodes to search from
   */
  private final GraphNodeEnumeration e;
  /**
   * an enumeration of child nodes for each node being searched
   */
  private final GraphNodeEnumeration[] info;

  /**
   * get the out edges of a given node
   *
   * @param n the node of which to get the out edges
   * @return the out edges
   */
  protected GraphNodeEnumeration getConnected(GraphNode n) {
    return n.outNodes();
  }
}
