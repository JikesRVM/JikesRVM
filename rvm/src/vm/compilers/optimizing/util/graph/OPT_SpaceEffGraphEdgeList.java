/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */


import  java.util.Enumeration;


//
// List of Graph Edges.
//
class OPT_SpaceEffGraphEdgeList
    implements Enumeration {
  OPT_SpaceEffGraphEdge _edge;
  OPT_SpaceEffGraphEdgeList _next;
  OPT_SpaceEffGraphEdgeList _prev;

  public boolean hasMoreElements() {
    if (_next == null)
      return  false; 
    else 
      return  true;
  }

  public Object nextElement() {
    OPT_SpaceEffGraphEdgeList tmp = _next;
    _next = _next._next;
    return  tmp;
  }

  public OPT_SpaceEffGraphEdge edge() {
    return  _edge;
  }

  public OPT_SpaceEffGraphEdgeList next() {
    return  _next;
  }

  public OPT_SpaceEffGraphEdgeList prev() {
    return  _prev;
  }

  public boolean inGraphEdgeList(OPT_SpaceEffGraphEdge edge) {
    OPT_SpaceEffGraphEdgeList n = this;
    while (n != null) {
      if (n._edge == edge)
        return  true;
      n = n._next;
    }
    return  false;
  }
}
