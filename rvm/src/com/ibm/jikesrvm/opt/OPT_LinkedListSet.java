/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.jikesrvm.opt;

import java.util.Iterator;
import  java.util.NoSuchElementException;
import  java.util.AbstractSet;
import  java.util.Set;
import  java.util.HashSet;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_LinkedListSet<T> extends AbstractSet<T> {
  OPT_LinkedListObjectElement<T> tos;
  boolean no_duplicates;

  public OPT_LinkedListSet () {
    no_duplicates = true;
  }

  int eliminateDuplicates () {
    Set<T> s = new HashSet<T>();
    OPT_LinkedListObjectElement<T> curr = tos;
    OPT_LinkedListObjectElement<T> prev = null;
    int size = 0;
    while (curr != null) {
      T v = curr.value;
      if (!s.add(v))
        prev.next = curr.next; 
      else {
        prev = curr;
        ++size;
      }
      curr = curr.nextElement();
    }
    no_duplicates = true;
    return  size;
  }

  public T pull () {
    if (tos == null)
      throw  new NoSuchElementException();
    T v = tos.value;
    tos = tos.nextElement();
    return  v;
  }

  public T pullLast () {
    if (tos == null)
      throw  new NoSuchElementException();
    OPT_LinkedListObjectElement<T> e = tos;
    OPT_LinkedListObjectElement<T> f = e;
    while (e.next != null) {
      f = e;
      e = e.nextElement();
    }
    T v = e.value;
    f.next = null;
    return  v;
  }

  public int size () {
    if (!no_duplicates)
      return  eliminateDuplicates();
    int size = 0;
    OPT_LinkedListObjectElement<T> e = tos;
    while (e != null) {
      ++size;
      e = e.nextElement();
    }
    return  size;
  }

  public boolean isEmpty () {
    return  tos == null;
  }

  public boolean contains (Object o) {
    OPT_LinkedListObjectElement<T> e = tos;
    while (e != null) {
      if (o.equals(e.value))
        return  true;
      e = e.nextElement();
    }
    return  false;
  }

  public Iterator<T> iterator () {
    if (!no_duplicates)
      eliminateDuplicates();
    return  new OPT_LinkedListSetIterator<T>(this);
  }

  public Object[] toArray () {
    int size = size();
    Object[] a = new Object[size];
    OPT_LinkedListObjectElement<T> e = tos;
    for (int i = 0; i < size; ++i) {
      a[i] = e.value;
      e = e.nextElement();
    }
    return  a;
  }

  /**
   * note: breaks java.util.Set spec, always returns true.
   * doesn't allow null.
   */
  public boolean add (T o) {
    OPT_LinkedListObjectElement<T> e = new OPT_LinkedListObjectElement<T>(o);
    e.next = tos;
    no_duplicates = (tos == null);
    tos = e;
    return  true;
  }

  public boolean remove (Object o) {
    if (no_duplicates) {
      if (tos == null)
        return  false;
      T v = tos.value;
      if (o.equals(v)) {
        tos = tos.nextElement();
        return  true;
      }
      OPT_LinkedListObjectElement<T> prev = tos;
      OPT_LinkedListObjectElement<T> curr = prev.nextElement();
      while (curr != null) {
        v = curr.value;
        if (o.equals(v)) {
          prev.next = curr.next;
          return  true;
        }
        prev = curr;
        curr = curr.nextElement();
      }
      return  false;
    } 
    else {
      if (tos == null)
        return  false;
      boolean result = false;
      Object v = tos.value;
      if (o.equals(v)) {
        tos = tos.nextElement();
        result = true;
      }
      OPT_LinkedListObjectElement<T> prev = tos;
      OPT_LinkedListObjectElement<T> curr = prev.nextElement();
      while (curr != null) {
        v = curr.value;
        if (o.equals(v)) {
          prev.next = curr.next;
          result = true;
        }
        prev = curr;
        curr = curr.nextElement();
      }
      return  result;
    }
  }

  public void clear () {
    tos = null;
    no_duplicates = true;
  }
}


class OPT_LinkedListSetIterator<T>
    implements Iterator<T> {
  OPT_LinkedListSet<T> s;
  OPT_LinkedListObjectElement<T> n, npp;

  OPT_LinkedListSetIterator (OPT_LinkedListSet<T> e) {
    s = e;
    n = s.tos;
  }

  public boolean hasNext () {
    return  n == null;
  }

  public T next () {
    if (n == null)
      throw  new NoSuchElementException();
    T v = n.value;
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
      npp = npp.nextElement();
    }
    n = n.nextElement();
    return  v;
  }

  public void remove () {
    if (npp == null) {
      if (n == s.tos) {
        // next() wasn't called
        throw  new IllegalStateException();
      } 
      else {
        // next() called once
        s.tos = s.tos.nextElement();
        return;
      }
    }
    npp.next = n;
  }
}
