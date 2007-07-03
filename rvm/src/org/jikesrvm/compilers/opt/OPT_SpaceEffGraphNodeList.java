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

import java.util.Enumeration;

/**
 * List of Graph nodes.
 *
 * comments: should a doubly linked list implement Enumeration?
 */
class OPT_SpaceEffGraphNodeList implements Enumeration<OPT_SpaceEffGraphNodeList> {
  OPT_SpaceEffGraphNode _node;
  OPT_SpaceEffGraphNodeList _next;
  OPT_SpaceEffGraphNodeList _prev;

  OPT_SpaceEffGraphNodeList() {
    _node = null;
    _next = null;
    _prev = null;
  }

  public boolean hasMoreElements() {
    return _next != null;
  }

  // return the next GraphNodeList element.
  public OPT_SpaceEffGraphNodeList nextElement() {
    OPT_SpaceEffGraphNodeList tmp = _next;
    _next = _next._next;
    return tmp;
  }

  OPT_SpaceEffGraphNode node() {
    return _node;
  }

  OPT_SpaceEffGraphNodeList next() {
    return _next;
  }

  OPT_SpaceEffGraphNodeList prev() {
    return _prev;
  }
}
