/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

/**
 * A generic interface for graph nodes.  All graph utilities should be
 * defined in terms of this interface, and all graph implementations
 * should make sure their nodes implement this interface.
 *
 *
 * @see OPT_Graph
 * @see OPT_GraphEdge
 * @see OPT_GraphUtilities
 */
public interface OPT_GraphNode extends OPT_GraphElement
{

  /** 
   * Get an enumeration of all the edges to which edges sourced at
   * this node point.
   * @return an enumeration of all the edges to which edges sourced
   * at this node point. 
   */
  OPT_GraphNodeEnumeration outNodes ();



  /** 
   * Get an enumeration of all the edges at which edges that point
   * to this node are sourced.
   * @return an enumeration of all the edges at which edges that
   * point to this node are sourced.
   */
  OPT_GraphNodeEnumeration inNodes ();



  /**
   *  The index of this node in its graph.  In general, this can e
   * anarbitrary number, but after a call to
   * {@link OPT_Graph#compactNodeNumbering
   * OPT_Graph.compactNodeNumbering} the nodes of a graph should be
   * numbered 0 thru (# of nodes in graph - 1). 
   *
   * @return the index of this node in its graph.
   */
  int getIndex ();

  void setIndex (int i);
}



