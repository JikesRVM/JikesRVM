/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

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

  /**
   * put your documentation comment here
   */
  OPT_SmallStack () {
    head = null;
  }

  /**
   * put your documentation comment here
   * @param   Object e
   */
  OPT_SmallStack (Object e) {
    head = new OPT_LinkedListObjectElement(e);
  }

  /**
   * put your documentation comment here
   * @param e
   * @return 
   */
  final Object push (Object e) {
    OPT_LinkedListObjectElement el = new OPT_LinkedListObjectElement(e);
    if (head != null)
      head.insertBefore(el);
    head = el;
    return  e;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final Object pop () {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    head = (OPT_LinkedListObjectElement)head.next;
    return  el.value;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final Object getTOS () {
    return  ((OPT_LinkedListObjectElement)head).value;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final Object peek () {
    return  getTOS();
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean isEmpty () {
    return  (head == null);
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final boolean empty () {
    return  isEmpty();
  }

  /**
   * put your documentation comment here
   * @param obj
   * @return 
   */
  final int search (Object obj) {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (int i = 0; el != null; ++i, el = 
        (OPT_LinkedListObjectElement)el.next) {
      if (el.value == obj)
        return  i;
    }
    return  -1;
  }

  /**
   * put your documentation comment here
   * @param s2
   * @return 
   */
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

  /**
   * put your documentation comment here
   * @return 
   */
  final OPT_SmallStack copy () {
    OPT_SmallStack s = new OPT_SmallStack();
    if (head == null)
      return  s;
    s.head = head.copyFrom();
    return  s;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final OPT_SmallStack shallowCopy () {
    OPT_SmallStack s = new OPT_SmallStack();
    s.head = head;
    return  s;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final int size () {
    int size = 0;
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (; el != null; el = (OPT_LinkedListObjectElement)el.next, ++size);
    return  size;
  }

  /**
   * put your documentation comment here
   * @param i
   * @return 
   */
  final Object getFromTop (int i) {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    for (; i > 0; el = (OPT_LinkedListObjectElement)el.next, --i);
    return  el.value;
  }

  /**
   * put your documentation comment here
   * @return 
   */
  final OPT_LinkedListObjectEnumerator elements () {
    return  new OPT_LinkedListObjectEnumerator(
        (OPT_LinkedListObjectElement)head);
  }

  /**
   * put your documentation comment here
   * @return 
   */
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



