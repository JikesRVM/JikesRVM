/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;

/**
 * OPT_Stack
 * OPT_Stack is a smaller implementation of java.util.Stack, that uses a linked
 * list rather than a vector.
 *
 * @author John Whaley
 * @date  3/18/98
 */
class OPT_Stack {
  OPT_LinkedListObjectElement head;

  OPT_Stack() {
    head = null;
  }

  OPT_Stack(Object e) {
    head = new OPT_LinkedListObjectElement(e);
  }

  final Object push(Object e) {
    OPT_LinkedListObjectElement el = new OPT_LinkedListObjectElement(e);
    if (head != null)
      head.insertBefore(el);
    head = el;
    return  e;
  }

  final Object pop() {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    head = (OPT_LinkedListObjectElement)head.getNext();
    return  el.getValue();
  }

  final Object getTOS() {
    return  ((OPT_LinkedListObjectElement)head).getValue();
  }

  final Object peek() {
    return  getTOS();
  }

  final boolean isEmpty() {
    return  (head == null);
  }

  final boolean empty() {
    return  isEmpty();
  }

  final int search(Object obj) {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (int i = 0; el != null; ++i, 
        el = (OPT_LinkedListObjectElement)el.getNext()) {
      if (el.getValue() == obj)
        return  i;
    }
    return  -1;
  }

  final boolean compare(OPT_Stack s2) {
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

  final OPT_Stack copy() {
    OPT_Stack s = new OPT_Stack();
    if (head == null)
      return  s;
    s.head = head.copyFrom();
    return  s;
  }

  final OPT_Stack shallowCopy() {
    OPT_Stack s = new OPT_Stack();
    s.head = head;
    return  s;
  }

  final OPT_LinkedListObjectEnumerator elements() {
    return  new OPT_LinkedListObjectEnumerator(
        (OPT_LinkedListObjectElement)head);
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
