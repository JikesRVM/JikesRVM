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
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_Queue {
  OPT_LinkedListElement head;
  OPT_LinkedListElement tail;
  OPT_LinkedListObjectElement free;

  OPT_Queue() {
    // head = tail = free = null;
  }

  OPT_Queue(Object e) {
    head = tail = new OPT_LinkedListObjectElement(e);
  }

  final Object insert(Object e) {
    OPT_LinkedListObjectElement el;
    if (free == null)
      el = new OPT_LinkedListObjectElement(e); 
    else {
      el = free;
      free = (OPT_LinkedListObjectElement)el.next;
      el.next = null;
      el.value = e;
    }
    if (head == null) {
      head = tail = el;
    } 
    else {
      tail.insertAfter(el);
      tail = el;
    }
    return  e;
  }

  final Object remove() {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    head = head.next;
    el.next = free;
    free = el;
    Object result = el.value;
    el.value = null;
    return  result;
  }

  final boolean isEmpty() {
    return  (head == null);
  }

  final OPT_LinkedListObjectEnumerator elements() {
    return  new OPT_LinkedListObjectEnumerator
        ((OPT_LinkedListObjectElement)head);
  }
}
