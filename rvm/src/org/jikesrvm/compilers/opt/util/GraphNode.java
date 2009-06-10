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
 * A generic interface for graph nodes.  All graph utilities should be
 * defined in terms of this interface, and all graph implementations
 * should make sure their nodes implement this interface.
 *
 *
 * @see Graph
 * @see GraphEdge
 * @see GraphUtilities
 */
public interface GraphNode extends GraphElement {

  /**
   * Get an enumeration of all the edges to which edges sourced at
   * this node point.
   * @return an enumeration of all the edges to which edges sourced
   * at this node point.
   */
  GraphNodeEnumeration outNodes();

  /**
   * Get an enumeration of all the edges at which edges that point
   * to this node are sourced.
   * @return an enumeration of all the edges at which edges that
   * point to this node are sourced.
   */
  GraphNodeEnumeration inNodes();

  /**
   *  The index of this node in its graph.  In general, this can e
   * anarbitrary number, but after a call to
   * {@link Graph#compactNodeNumbering
   * Graph.compactNodeNumbering} the nodes of a graph should be
   * numbered 0 thru (# of nodes in graph - 1).
   *
   * @return the index of this node in its graph.
   */
  int getIndex();

  void setIndex(int i);
}



