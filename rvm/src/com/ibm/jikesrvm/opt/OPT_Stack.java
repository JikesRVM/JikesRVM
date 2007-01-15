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
 * OPT_Stack
 * OPT_Stack is a smaller implementation of java.util.Stack, that uses a linked
 * list rather than a vector.
 *
 * @author John Whaley
 * @date  3/18/98
 */
public class OPT_Stack<T> {
  OPT_LinkedListObjectElement head;

  public OPT_Stack() {
    head = null;
  }

  public OPT_Stack(T e) {
    head = new OPT_LinkedListObjectElement(e);
  }

  public final Object push(T e) {
    OPT_LinkedListObjectElement el = new OPT_LinkedListObjectElement(e);
    if (head != null)
      head.insertBefore(el);
    head = el;
    return  e;
  }

  public final T pop() {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    head = (OPT_LinkedListObjectElement)head.getNext();
    return  (T)el.getValue();
  }

  public final T getTOS() {
    return  (T)((OPT_LinkedListObjectElement)head).getValue();
  }

  public final T peek() {
    return  getTOS();
  }

  public final boolean isEmpty() {
    return  (head == null);
  }

  public final boolean empty() {
    return  isEmpty();
  }

  public final int search(T obj) {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (int i = 0; el != null; ++i, 
        el = (OPT_LinkedListObjectElement)el.getNext()) {
      if (el.getValue() == obj)
        return  i;
    }
    return  -1;
  }

  public final boolean compare(OPT_Stack<T> s2) {
    OPT_LinkedListObjectElement p1 = this.head;
    OPT_LinkedListObjectElement p2 = s2.head;
    for (;;) {
      if (p1 == null)
        return  (p2 == null);
      if (p2 == null)
        return  false;
      if (p1.getValue() != p2.getValue())
        return  false;
      p1 = (OPT_LinkedListObjectElement)p1.getNext();
      p2 = (OPT_LinkedListObjectElement)p2.getNext();
    }
  }

  public final OPT_Stack<T> copy() {
    OPT_Stack<T> s = new OPT_Stack<T>();
    if (head == null)
      return  s;
    s.head = head.copyFrom();
    return  s;
  }

  public final OPT_Stack<T> shallowCopy() {
    OPT_Stack<T> s = new OPT_Stack<T>();
    s.head = head;
    return  s;
  }

  public final OPT_LinkedListObjectEnumerator elements() {
    return  new OPT_LinkedListObjectEnumerator((OPT_LinkedListObjectElement)head);
  }

  public String toString() {
    StringBuffer sb = new StringBuffer(" --> ");
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (; el != null; el = (OPT_LinkedListObjectElement)el.getNext()) {
      sb.append(el.getValue().toString());
      sb.append(' ');
    }
    return  sb.toString();
  }
}
