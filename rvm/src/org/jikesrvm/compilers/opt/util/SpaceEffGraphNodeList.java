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

import java.util.Enumeration;


/**
 * List of Graph nodes.
 *
 * comments: should a doubly linked list implement Enumeration?
 */
class SpaceEffGraphNodeList implements Enumeration<SpaceEffGraphNodeList> {
  SpaceEffGraphNode _node;
  SpaceEffGraphNodeList _next;
  SpaceEffGraphNodeList _prev;

  SpaceEffGraphNodeList() {
    _node = null;
    _next = null;
    _prev = null;
  }

  public boolean hasMoreElements() {
    return _next != null;
  }

  // return the next GraphNodeList element.
  public SpaceEffGraphNodeList nextElement() {
    SpaceEffGraphNodeList tmp = _next;
    _next = _next._next;
    return tmp;
  }

  SpaceEffGraphNode node() {
    return _node;
  }

  SpaceEffGraphNodeList next() {
    return _next;
  }

  SpaceEffGraphNodeList prev() {
    return _prev;
  }
}
