/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import  java.util.NoSuchElementException;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_LinkedListSet extends java.util.AbstractSet {
  OPT_LinkedListObjectElement tos;
  boolean no_duplicates;

  /**
   * put your documentation comment here
   */
  public OPT_LinkedListSet () {
    no_duplicates = true;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  int eliminateDuplicates () {
    java.util.Set s = new java.util.HashSet();
    OPT_LinkedListObjectElement curr = tos;
    OPT_LinkedListObjectElement prev = null;
    int size = 0;
    while (curr != null) {
      Object v = curr.value;
      if (!s.add(v))
        prev.next = curr.next; 
      else {
        prev = curr;
        ++size;
      }
      curr = (OPT_LinkedListObjectElement)curr.next;
    }
    no_duplicates = true;
    return  size;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object pull () {
    if (tos == null)
      throw  new NoSuchElementException();
    Object v = tos.value;
    tos = (OPT_LinkedListObjectElement)tos.next;
    return  v;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object pullLast () {
    if (tos == null)
      throw  new NoSuchElementException();
    OPT_LinkedListElement e = tos;
    OPT_LinkedListElement f = e;
    while (e.next != null) {
      f = e;
      e = e.next;
    }
    Object v = ((OPT_LinkedListObjectElement)e).value;
    f.next = null;
    return  v;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public int size () {
    if (!no_duplicates)
      return  eliminateDuplicates();
    int size = 0;
    OPT_LinkedListObjectElement e = tos;
    while (e != null) {
      ++size;
      e = (OPT_LinkedListObjectElement)e.next;
    }
    return  size;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean isEmpty () {
    return  tos == null;
  }

  /**
   * put your documentation comment here
   * @param o
   * @return 
   */
  public boolean contains (Object o) {
    OPT_LinkedListObjectElement e = tos;
    while (e != null) {
      if (o.equals(e.value))
        return  true;
      e = (OPT_LinkedListObjectElement)e.next;
    }
    return  false;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public java.util.Iterator iterator () {
    if (!no_duplicates)
      eliminateDuplicates();
    return  new OPT_LinkedListSetIterator(this);
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object[] toArray () {
    int size = size();
    Object[] a = new Object[size];
    OPT_LinkedListObjectElement e = tos;
    for (int i = 0; i < size; ++i) {
      a[i] = e.value;
      e = (OPT_LinkedListObjectElement)e.next;
    }
    return  a;
  }

  // note: breaks java.util.Set spec, always returns true.
  // doesn't allow null.
  public boolean add (Object o) {
    OPT_LinkedListObjectElement e = new OPT_LinkedListObjectElement(o);
    e.next = tos;
    no_duplicates = (tos == null);
    tos = e;
    return  true;
  }

  /**
   * put your documentation comment here
   * @param o
   * @return 
   */
  public boolean remove (Object o) {
    if (no_duplicates) {
      if (tos == null)
        return  false;
      Object v = tos.value;
      if (o.equals(v)) {
        tos = (OPT_LinkedListObjectElement)tos.next;
        return  true;
      }
      OPT_LinkedListObjectElement prev = tos;
      OPT_LinkedListObjectElement curr = (OPT_LinkedListObjectElement)prev.next;
      while (curr != null) {
        v = curr.value;
        if (o.equals(v)) {
          prev.next = curr.next;
          return  true;
        }
        prev = curr;
        curr = (OPT_LinkedListObjectElement)curr.next;
      }
      return  false;
    } 
    else {
      if (tos == null)
        return  false;
      boolean result = false;
      Object v = tos.value;
      if (o.equals(v)) {
        tos = (OPT_LinkedListObjectElement)tos.next;
        result = true;
      }
      OPT_LinkedListObjectElement prev = tos;
      OPT_LinkedListObjectElement curr = (OPT_LinkedListObjectElement)prev.next;
      while (curr != null) {
        v = curr.value;
        if (o.equals(v)) {
          prev.next = curr.next;
          result = true;
        }
        prev = curr;
        curr = (OPT_LinkedListObjectElement)curr.next;
      }
      return  result;
    }
  }

  /**
   * put your documentation comment here
   */
  public void clear () {
    tos = null;
    no_duplicates = true;
  }
}


/**
 * put your documentation comment here
 */
class OPT_LinkedListSetIterator
    implements java.util.Iterator {
  OPT_LinkedListSet s;
  OPT_LinkedListObjectElement n, npp;

  /**
   * put your documentation comment here
   * @param   OPT_LinkedListSet e
   */
  OPT_LinkedListSetIterator (OPT_LinkedListSet e) {
    s = e;
    n = s.tos;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public boolean hasNext () {
    return  n == null;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  public Object next () {
    if (n == null)
      throw  new NoSuchElementException();
    Object v = n.value;
    if (npp == null) {
      if (n == s.tos) {
      // returning first element
      } 
      else {
        // returning second element
        npp = s.tos;
      }
    } 
    else {
      npp = (OPT_LinkedListObjectElement)npp.next;
    }
    n = (OPT_LinkedListObjectElement)n.next;
    return  v;
  }

  /**
   * put your documentation comment here
   */
  public void remove () {
    if (npp == null) {
      if (n == s.tos) {
        // next() wasn't called
        throw  new IllegalStateException();
      } 
      else {
        // next() called once
        s.tos = (OPT_LinkedListObjectElement)s.tos.next;
        return;
      }
    }
    npp.next = n;
  }
}



