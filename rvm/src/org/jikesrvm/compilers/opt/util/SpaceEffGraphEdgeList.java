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


class SpaceEffGraphEdgeList implements Enumeration<SpaceEffGraphEdgeList> {
  SpaceEffGraphEdge _edge;
  SpaceEffGraphEdgeList _next;
  SpaceEffGraphEdgeList _prev;

  @Override
  public boolean hasMoreElements() {
    return _next != null;
  }

  @Override
  public SpaceEffGraphEdgeList nextElement() {
    SpaceEffGraphEdgeList tmp = _next;
    _next = _next._next;
    return tmp;
  }

  public SpaceEffGraphEdge edge() {
    return _edge;
  }

  public SpaceEffGraphEdgeList next() {
    return _next;
  }

  public SpaceEffGraphEdgeList prev() {
    return _prev;
  }

  public boolean inGraphEdgeList(SpaceEffGraphEdge edge) {
    SpaceEffGraphEdgeList n = this;
    while (n != null) {
      if (n._edge == edge) {
        return true;
      }
      n = n._next;
    }
    return false;
  }
}
