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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


/**
 * This class implements depth-first search over a Graph,
 * return an enumeration of the nodes of the graph in order of
 * increasing finishing time.  This class follows the outNodes of the
 * graph nodes to define the graph, but this behavior can be changed
 * by overriding the getConnected method.
 */
public class DFSenumerateByFinish extends Stack<GraphNode> implements Enumeration<GraphNode> {

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
  public DFSenumerateByFinish(Graph net, Enumeration<GraphNode> nodes) {
    e = nodes;
    net.compactNodeNumbering();
    info = new ArrayList<Enumeration<GraphNode>>(net.numberOfNodes() + 1);
    //  info = new java.util.HashMap( net.numberOfNodes() );
    if (e.hasMoreElements()) {
      theNextElement = e.nextElement();
    }
  }

  /**
   * While a depth-first enumeration is in progress, this field
   * holds the current root node, i.e. the current bottom of the
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
    return (!empty() || (theNextElement != null && info.get(theNextElement.getIndex()) == null));
  }

  /**
   *  Find the next graph node in finishing time order.
   *
   *  @see #nextElement
   *
   *  @return the next graph node in finishing time order.
   */
  @Override
  public GraphNode nextElement() {
    if (empty()) {
      GraphNode v = theNextElement;
      currentRoot = theNextElement;
      info.set(v.getIndex(), getConnected(v));
      push(v);
    }
    recurse:
    while (!empty()) {
      GraphNode v = peek();
      Enumeration<GraphNode> pendingChildren = info.get(v.getIndex());
      for (Enumeration<GraphNode> e = pendingChildren; e.hasMoreElements();) {
        GraphNode n = e.nextElement();
        Enumeration<GraphNode> nChildren = info.get(n.getIndex());
        if (nChildren == null) {
          // found a new child: recurse to it.
          info.set(n.getIndex(), getConnected(n));
          push(n);
          continue recurse;
        }
      }
      // no more children to visit: finished this vertex
      while (info.get(theNextElement.getIndex()) != null && e.hasMoreElements()) {
        theNextElement = e.nextElement();
      }
      return pop();
    }
    return null;
  }

  /**
   * the current next element in finishing time order
   */
  private GraphNode theNextElement;
  /**
   * an enumeration of all nodes to search from
   */
  private final Enumeration<GraphNode> e;
  /**
   * an enumeration of child nodes for each node being searched
   */
  private final List<Enumeration<GraphNode>> info;

  /**
   * get the out edges of a given node
   *
   * @param n the node of which to get the out edges
   * @return the out edges
   */
  protected Enumeration<GraphNode> getConnected(GraphNode n) {
    return n.outNodes();
  }
}
