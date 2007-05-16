/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
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
