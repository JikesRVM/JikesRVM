/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.Enumeration;


//
// List of Graph nodes. 
// 
// author Harini Srinivasan.
// comments: should a doubly linked list implement Enumeration?
class OPT_SpaceEffGraphNodeList
    implements Enumeration {
  OPT_SpaceEffGraphNode _node;
  OPT_SpaceEffGraphNodeList _next;
  OPT_SpaceEffGraphNodeList _prev;

  /**
   * put your documentation comment here
   */
  OPT_SpaceEffGraphNodeList () {
    _node = null;
    _next = null;
    _prev = null;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasMoreElements () {
    if (_next == null)
      return  false; 
    else 
      return  true;
  }

  // return the next GraphNodeList element.
  public Object nextElement () {
    OPT_SpaceEffGraphNodeList tmp = _next;
    _next = _next._next;
    return  tmp;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_SpaceEffGraphNode node () {
    return  _node;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_SpaceEffGraphNodeList next () {
    return  _next;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_SpaceEffGraphNodeList prev () {
    return  _prev;
  }
  /*
   public boolean add(OPT_SpaceEffGraphNode node) {
   OPT_SpaceEffGraphNodeList p = this;
   OPT_SpaceEffGraphNodeList prev = this;
   while (p != null) 
   {
   if (p._node == null)  { 
   // will be the case for first node.
   p._node = node;
   return true;
   }
   if (p._node == node) 
   // node already in list.
   return false;
   prev = p;
   p = p._next;
   }
   prev._next = new OPT_SpaceEffGraphNodeList();
   prev._next._node = node;
   prev._next._prev = prev;  // doubly linked list.
   return true;
   }
   */
}



