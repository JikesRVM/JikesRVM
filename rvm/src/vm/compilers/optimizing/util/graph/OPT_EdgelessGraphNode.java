/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;

/**
 * This is a general implementation of graph nodes for graphs without
 * explicit edges.  It co-operates with OPT_EdgelessGraph to implement
 * a graph for which are recorded with node hash tables in the nodes.
 *
 * @author Julian Dolby
 *
 * @see OPT_EdgelessGraph
 *
 */
class OPT_EdgelessGraphNode implements OPT_GraphNode, Serializable {
  /**
   * The index of this node in its graph.  These numbers always
   * number from 0 to (# of nodes in graph - 1)
   */
  private int index;
  /**
   *  The set of nodes that have edges pointing to this node.
   */
  private HashSet inEdges = null;

  /**
   *  The set of nodes to which this node has edges.
   */
  private HashSet outEdges = null;

  /**
   *  A scratch field in int type
   * @deprecated
   */
  private int scratch;
  /**
   *  A scratch field in Object type
   * @deprecated
   */
  private Object scratchObject;

  /**
   *  Read the int-typed scratch field
   * @return the  current value of the int scratch field
   * @deprecated
   */
  public int getScratch() {
    return  scratch;
  }

  /**
   *  Set the int-typed scratch field
   * @param scratch the new value of the int scratch field
   * @return the new value of the int scratch field
   * @deprecated
   */
  public int setScratch(int scratch) {
    return  this.scratch = scratch;
  }

  /**
   *  Read the Object-typed scratch field
   * @return the current value of the Object scratch field
   * @deprecated
   */
  public Object getScratchObject() {
    return  scratchObject;
  }

  /**
   *  Set the Object-typed scratch field
   * @param scratch the new value of the Object scratch field
   * @return the new value of the Object scratch field
   * @deprecated
   */
  public Object setScratchObject(Object scratch) {
    return  this.scratchObject = scratch;
  }

  /**
   *  Encapsulates an enumeration of nodes.  This class is needed
   * due to the idiotic decision to have Iterator and Enumeration
   * types in the java libraries.
   */
  static class NodeEnumeration
      implements OPT_GraphNodeEnumeration {
    private Iterator e;

    NodeEnumeration(Iterator e) {
      this.e = e;
    }

    public boolean hasMoreElements() {
      return  e.hasNext();
    }

    public OPT_GraphNode next() {
      return  (OPT_GraphNode)e.next();
    }

    public Object nextElement() {
      return  next();
    }
  }

  /**
   * Set the index number of this node.  This method should only be
   * used by the addGraphNode method of OPT_Graph.
   *
   * @param index the new index number of the node
   * @return the new index number of the node
   *
   * @see OPT_Graph#addGraphNode
   */
  public void setIndex(int index) {
    this.index = index;
  }

  /**
   * Get the index number of this node.  
   *
   * @return the index number of the node
   */
  public int getIndex() {
    return  index;
  }

  /**
   * Add an edge from this node to another given node; this is only
   * to be called by OPT_Graph.addGraphEdge.
   *
   * @param n the node to which to add an edge from this node.
   *
   * @see OPT_Graph#addGraphEdge
   */
  void addOutEdgeInternal(OPT_EdgelessGraphNode n) {
    if (outEdges == null) outEdges = new HashSet();
    outEdges.add(n);
  }

  /**
   * Add an edge from another given node to this node; this is only
   * to be called by OPT_Graph.addGraphEdge.
   *
   * @param n the node from which to add an edge to this node.
   *
   * @see OPT_Graph#addGraphEdge
   */
  void addInEdgeInternal(OPT_EdgelessGraphNode n) {
    if (inEdges == null) inEdges = new HashSet();
    inEdges.add(n);
  }

  /**
   * Enumerate the nodes to which this node has edges.
   *
   * @return enumeration of nodes to which this node has an edge
   *
   */
  public OPT_GraphNodeEnumeration outNodes() {
    return  new NodeEnumeration(
      outEdges==null? OPT_EmptyIterator.INSTANCE: outEdges.iterator());
  }

  /**
   * Enumerate the nodes that have an edge to this node.
   *
   * @return enumeration of nodes that have an edge to this node.
   *
   */
  public OPT_GraphNodeEnumeration inNodes() {
    return  new NodeEnumeration(
      inEdges==null? OPT_EmptyIterator.INSTANCE: inEdges.iterator());
  }

  /**
   * Find out whether this node has an edge to a given node
   *
   * @param x the node to check whether this node has an edge to it
   * @return true if there is an edge from this node to x
   */
  public boolean hasOut(OPT_GraphNode x) {
    return  outEdges!=null && outEdges.contains(x);
  }

  /**
   * Find out whether a given node has an edge to this node 
   *
   * @param x the node to check whether it has an edge to this node
   * @return true if there is an edge from x to this node
   */
  public boolean hasIn(OPT_GraphNode x) {
    return  inEdges!=null && inEdges.contains(x);
  }
}



