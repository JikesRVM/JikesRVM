/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.util.Enumeration;

/**
 * OPT_SmallStack
 * Stack is a smaller implementation of java.util.Stack, that uses a linked
 * list rather than a vector.
 *
 * @author John Whaley
 */
class OPT_SmallStack {
  OPT_LinkedListObjectElement head;

  OPT_SmallStack () {
    head = null;
  }

  OPT_SmallStack (Object e) {
    head = new OPT_LinkedListObjectElement(e);
  }

  final Object push (Object e) {
    OPT_LinkedListObjectElement el = new OPT_LinkedListObjectElement(e);
    if (head != null)
      head.insertBefore(el);
    head = el;
    return  e;
  }

  final Object pop () {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    head = (OPT_LinkedListObjectElement)head.next;
    return  el.value;
  }

  final Object getTOS () {
    return  ((OPT_LinkedListObjectElement)head).value;
  }

  final Object peek () {
    return  getTOS();
  }

  final boolean isEmpty () {
    return  (head == null);
  }

  final boolean empty () {
    return  isEmpty();
  }

  final int search (Object obj) {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (int i = 0; el != null; ++i, el = 
        (OPT_LinkedListObjectElement)el.next) {
      if (el.value == obj)
        return  i;
    }
    return  -1;
  }

  final boolean compare (OPT_SmallStack s2) {
    OPT_LinkedListObjectElement p1 = this.head;
    OPT_LinkedListObjectElement p2 = s2.head;
    for (;;) {
      if (p1 == null)
        return  (p2 == null);
      if (p2 == null)
        return  false;
      if (p1.value != p2.value)
        return  false;
      p1 = (OPT_LinkedListObjectElement)p1.next;
      p2 = (OPT_LinkedListObjectElement)p2.next;
    }
  }

  final OPT_SmallStack copy () {
    OPT_SmallStack s = new OPT_SmallStack();
    if (head == null)
      return  s;
    s.head = head.copyFrom();
    return  s;
  }

  final OPT_SmallStack shallowCopy () {
    OPT_SmallStack s = new OPT_SmallStack();
    s.head = head;
    return  s;
  }

  final int size () {
    int size = 0;
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (; el != null; el = (OPT_LinkedListObjectElement)el.next, ++size);
    return  size;
  }

  final Object getFromTop (int i) {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (; i > 0; el = (OPT_LinkedListObjectElement)el.next, --i);
    return  el.value;
  }

  final OPT_LinkedListObjectEnumerator elements () {
    return  new OPT_LinkedListObjectEnumerator(
        (OPT_LinkedListObjectElement)head);
  }

  public String toString () {
    StringBuffer sb = new StringBuffer(" --> ");
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (; el != null; el = (OPT_LinkedListObjectElement)el.next) {
      sb.append(el.value.toString());
      sb.append(' ');
    }
    return  sb.toString();
  }
}



