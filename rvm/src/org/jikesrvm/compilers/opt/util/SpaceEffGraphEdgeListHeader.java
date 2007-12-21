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
package org.jikesrvm.compilers.opt.util;


class SpaceEffGraphEdgeListHeader {
  SpaceEffGraphEdgeList _first;
  SpaceEffGraphEdgeList _last;

  SpaceEffGraphEdgeList first() {
    return _first;
  }

  SpaceEffGraphEdgeList last() {
    return _last;
  }

  public void append(SpaceEffGraphEdge edge) {
    SpaceEffGraphEdgeList p = new SpaceEffGraphEdgeList();
    p._edge = edge;
    SpaceEffGraphEdgeList last = _last;
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

  public void add(SpaceEffGraphEdge edge) {
    SpaceEffGraphEdgeList p = first();
    SpaceEffGraphEdgeList prev = first();
    if (p == null) {
      // will be the case for first node.
      p = new SpaceEffGraphEdgeList();
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
    prev._next = new SpaceEffGraphEdgeList();
    prev._next._edge = edge;
    prev._next._prev = prev;                    // doubly linked list.
    _last = prev._next;
  }
}
