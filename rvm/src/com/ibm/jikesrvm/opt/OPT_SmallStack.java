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

/**
 * OPT_SmallStack
 * Stack is a smaller implementation of java.util.Stack, that uses a linked
 * list rather than a vector.
 *
 * @author John Whaley
 */
class OPT_SmallStack<T> {
  OPT_LinkedListObjectElement<T> head;

  OPT_SmallStack () {
    head = null;
  }

  OPT_SmallStack (T e) {
    head = new OPT_LinkedListObjectElement<T>(e);
  }

  final T push (T e) {
    OPT_LinkedListObjectElement<T> el = new OPT_LinkedListObjectElement<T>(e);
    if (head != null)
      head.insertBefore(el);
    head = el;
    return  e;
  }

  final T pop () {
    OPT_LinkedListObjectElement<T> el = head;
    head = head.nextElement();
    return  el.value;
  }

  final T getTOS () {
    return  head.value;
  }

  final T peek () {
    return  getTOS();
  }

  final boolean isEmpty () {
    return  (head == null);
  }

  final boolean empty () {
    return  isEmpty();
  }

  final int search (T obj) {
    OPT_LinkedListObjectElement<T> el = head;
    for (int i = 0; el != null; ++i, el = el.nextElement()) {
      if (el.value == obj)
        return  i;
    }
    return  -1;
  }

  final boolean compare (OPT_SmallStack<T> s2) {
    OPT_LinkedListObjectElement<T> p1 = this.head;
    OPT_LinkedListObjectElement<T> p2 = s2.head;
    for (;;) {
      if (p1 == null)
        return  (p2 == null);
      if (p2 == null)
        return  false;
      if (p1.value != p2.value)
        return  false;
      p1 = p1.nextElement();
      p2 = p2.nextElement();
    }
  }

  final OPT_SmallStack<T> copy () {
    OPT_SmallStack<T> s = new OPT_SmallStack<T>();
    if (head == null)
      return  s;
    s.head = head.copyFrom();
    return  s;
  }

  final OPT_SmallStack<T> shallowCopy () {
    OPT_SmallStack<T> s = new OPT_SmallStack<T>();
    s.head = head;
    return  s;
  }

  final int size () {
    int size = 0;
    OPT_LinkedListObjectElement<T> el = head;
    for (; el != null; el = el.nextElement(), ++size);
    return  size;
  }

  final Object getFromTop (int i) {
    OPT_LinkedListObjectElement<T> el = head;
    for (; i > 0; el = el.nextElement(), --i);
    return  el.value;
  }

  final OPT_LinkedListObjectEnumerator<T> elements () {
    return  new OPT_LinkedListObjectEnumerator<T>(head);
  }

  public String toString () {
    StringBuffer sb = new StringBuffer(" --> ");
    OPT_LinkedListObjectElement<T> el = head;
    for (; el != null; el = el.nextElement()) {
      sb.append(el.value.toString());
      sb.append(' ');
    }
    return  sb.toString();
  }
}



