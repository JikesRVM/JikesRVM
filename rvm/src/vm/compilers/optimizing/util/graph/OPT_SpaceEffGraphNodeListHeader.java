/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
final class OPT_SpaceEffGraphNodeListHeader {
  OPT_SpaceEffGraphNodeList _first;
  OPT_SpaceEffGraphNodeList _last;

  // constructor.
  /* no need for this, automatically set to null
   OPT_SpaceEffGraphNodeListHeader()
   {
   _first = null;
   _last = null;
   }
   */
  OPT_SpaceEffGraphNodeList first () {
    return  _first;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_SpaceEffGraphNodeList last () {
    return  _last;
  }

  /**
   * put your documentation comment here
   * @param node
   */
  public void append (OPT_SpaceEffGraphNode node) {
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

  /**
   * put your documentation comment here
   * @param node
   * @return 
   */
  public boolean add (OPT_SpaceEffGraphNode node) {
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



