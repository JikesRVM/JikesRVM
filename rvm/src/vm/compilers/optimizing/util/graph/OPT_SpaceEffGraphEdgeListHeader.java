/*
 * (C) Copyright IBM Corp. 2001
 */
/**
 * put your documentation comment here
 */
class OPT_SpaceEffGraphEdgeListHeader {
  OPT_SpaceEffGraphEdgeList _first;
  OPT_SpaceEffGraphEdgeList _last;

  // constructor.
  /*
   OPT_SpaceEffGraphEdgeListHeader()
   {
   // automatically init to null
   //_first = null;
   //_last = null;
   }
   */
  OPT_SpaceEffGraphEdgeList first () {
    return  _first;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  OPT_SpaceEffGraphEdgeList last () {
    return  _last;
  }

  /**
   * put your documentation comment here
   * @param edge
   */
  public void append (OPT_SpaceEffGraphEdge edge) {
    OPT_SpaceEffGraphEdgeList p = new OPT_SpaceEffGraphEdgeList();
    p._edge = edge;
    OPT_SpaceEffGraphEdgeList last = _last;
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
   * @param edge
   */
  public void add (OPT_SpaceEffGraphEdge edge) {
    OPT_SpaceEffGraphEdgeList p = first();
    OPT_SpaceEffGraphEdgeList prev = first();
    if (p == null) {
      // will be the case for first node.
      p = new OPT_SpaceEffGraphEdgeList();
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
    prev._next = new OPT_SpaceEffGraphEdgeList();
    prev._next._edge = edge;
    prev._next._prev = prev;                    // doubly linked list.
    _last = prev._next;
    return;
  }
  /*
   //
   // Multigraphs are possible. Therefore, an edge can be added more
   // than once. The inGraphEdgeList(..) method can be used to 
   // add an edge between two nodes only once.
   //
   public void add(OPT_SpaceEffGraphEdge edge) {
   OPT_SpaceEffGraphEdgeList prev = this;
   OPT_SpaceEffGraphEdgeList n = this;
   while (n != null) {
   prev = n;
   n = n._next;
   }
   prev._next = new OPT_SpaceEffGraphEdgeList();
   prev._next._edge = edge;
   }
   //
   // append an edge to the list. to do this, we need some kind of
   // placeholder that will say which element is the first element
   // of the list. So, simply make the first element of the list 
   // the header. It's _edge value will be null.
   //
   public void append(OPT_SpaceEffGraphEdge edge) {
   OPT_SpaceEffGraphEdgeList prev;
   OPT_SpaceEffGraphEdgeList n = this;
   // first element will be null. `this' points to this element.
   
   prev = new OPT_SpaceEffGraphEdgeList();
   prev._edge = edge;  // set edge.
   prev._next = n._next; // next is the rest of the list, but the first one.
   n._next = prev;      // _next of first element points to newly added one.
   }
   */
}



