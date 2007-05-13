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
 */
final class OPT_SpaceEffGraphNodeListHeader {
  OPT_SpaceEffGraphNodeList _first;
  OPT_SpaceEffGraphNodeList _last;

  OPT_SpaceEffGraphNodeList first() {
    return  _first;
  }

  OPT_SpaceEffGraphNodeList last() {
    return  _last;
  }

  public void append(OPT_SpaceEffGraphNode node) {
    OPT_SpaceEffGraphNodeList p = new OPT_SpaceEffGraphNodeList();
    p._node = node;
    OPT_SpaceEffGraphNodeList last = _last;
    if (last == null) {
      // will be the case for first edge.
      _first = p;
      _last = p;
    } 
    else {
      // there is at least one node.
      last._next = p;
      p._prev = last;           // doubly linked list.
      _last = p;
    }
  }

  public boolean add(OPT_SpaceEffGraphNode node) {
    OPT_SpaceEffGraphNodeList p = first();
    OPT_SpaceEffGraphNodeList prev = first();
    if (p == null) {
      // will be the case for first node.
      p = new OPT_SpaceEffGraphNodeList();
      p._node = node;
      _first = p;
      _last = p;
      return  true;
    }
    while (p != null) {
      if (p._node == node)
        // node already in list.
        return  false;
      prev = p;
      p = p._next;
    }
    prev._next = new OPT_SpaceEffGraphNodeList();
    prev._next._node = node;
    prev._next._prev = prev;                    // doubly linked list.
    _last = prev._next;
    return  true;
  }
}
