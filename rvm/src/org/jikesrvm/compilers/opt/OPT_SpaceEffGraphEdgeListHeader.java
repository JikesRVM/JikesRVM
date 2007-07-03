/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

class OPT_SpaceEffGraphEdgeListHeader {
  OPT_SpaceEffGraphEdgeList _first;
  OPT_SpaceEffGraphEdgeList _last;

  OPT_SpaceEffGraphEdgeList first() {
    return _first;
  }

  OPT_SpaceEffGraphEdgeList last() {
    return _last;
  }

  public void append(OPT_SpaceEffGraphEdge edge) {
    OPT_SpaceEffGraphEdgeList p = new OPT_SpaceEffGraphEdgeList();
    p._edge = edge;
    OPT_SpaceEffGraphEdgeList last = _last;
    if (last == null) {
      // will be the case for first edge.
      _first = p;
      _last = p;
    } else {
      // there is at least one node.
      last._next = p;
      p._prev = last;           // doubly linked list.
      _last = p;
    }
  }

  public void add(OPT_SpaceEffGraphEdge edge) {
    OPT_SpaceEffGraphEdgeList p = first();
    OPT_SpaceEffGraphEdgeList prev = first();
    if (p == null) {
      // will be the case for first node.
      p = new OPT_SpaceEffGraphEdgeList();
      p._edge = edge;
      _first = p;
      _last = p;
      return;
    }
    // we can have multigraphs. So, allow for multiple edges
    // between two nodes.
    while (p != null) {
      prev = p;
      p = p._next;
    }
    prev._next = new OPT_SpaceEffGraphEdgeList();
    prev._next._edge = edge;
    prev._next._prev = prev;                    // doubly linked list.
    _last = prev._next;
  }
}
