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

final class OPT_SpaceEffGraphNodeListHeader {
  OPT_SpaceEffGraphNodeList _first;
  OPT_SpaceEffGraphNodeList _last;

  OPT_SpaceEffGraphNodeList first() {
    return _first;
  }

  OPT_SpaceEffGraphNodeList last() {
    return _last;
  }

  public void append(OPT_SpaceEffGraphNode node) {
    OPT_SpaceEffGraphNodeList p = new OPT_SpaceEffGraphNodeList();
    p._node = node;
    OPT_SpaceEffGraphNodeList last = _last;
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

  public boolean add(OPT_SpaceEffGraphNode node) {
    OPT_SpaceEffGraphNodeList p = first();
    OPT_SpaceEffGraphNodeList prev = first();
    if (p == null) {
      // will be the case for first node.
      p = new OPT_SpaceEffGraphNodeList();
      p._node = node;
      _first = p;
      _last = p;
      return true;
    }
    while (p != null) {
      if (p._node == node) {
        // node already in list.
        return false;
      }
      prev = p;
      p = p._next;
    }
    prev._next = new OPT_SpaceEffGraphNodeList();
    prev._next._node = node;
    prev._next._prev = prev;                    // doubly linked list.
    _last = prev._next;
    return true;
  }
}
