/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.Enumeration;


/**
 * List of Graph nodes. 
 * 
 * comments: should a doubly linked list implement Enumeration?
 *
 * @author Harini Srinivasan.
 */
class OPT_SpaceEffGraphNodeList
    implements Enumeration {
  OPT_SpaceEffGraphNode _node;
  OPT_SpaceEffGraphNodeList _next;
  OPT_SpaceEffGraphNodeList _prev;

  OPT_SpaceEffGraphNodeList() {
    _node = null;
    _next = null;
    _prev = null;
  }

  public boolean hasMoreElements() {
    if (_next == null)
      return  false; 
    else 
      return  true;
  }

  // return the next GraphNodeList element.
  public Object nextElement() {
    OPT_SpaceEffGraphNodeList tmp = _next;
    _next = _next._next;
    return  tmp;
  }

  OPT_SpaceEffGraphNode node() {
    return  _node;
  }

  OPT_SpaceEffGraphNodeList next() {
    return  _next;
  }

  OPT_SpaceEffGraphNodeList prev() {
    return  _prev;
  }
}
