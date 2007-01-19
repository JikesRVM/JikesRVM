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
final class OPT_Queue<T> {
  OPT_LinkedListElement head;
  OPT_LinkedListElement tail;
  OPT_LinkedListObjectElement<T> free;

  OPT_Queue() {
    // head = tail = free = null;
  }

  OPT_Queue(T e) {
    head = tail = new OPT_LinkedListObjectElement<T>(e);
  }

  final T insert(T e) {
    OPT_LinkedListObjectElement<T> el;
    if (free == null)
      el = new OPT_LinkedListObjectElement<T>(e); 
    else {
      el = free;
      free = el.nextElement();
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

  final T remove() {
    @SuppressWarnings("unchecked") // This data structure should be re-thought
    OPT_LinkedListObjectElement<T> el = (OPT_LinkedListObjectElement)head;
    head = head.next;
    el.next = free;
    free = el;
    T result = el.value;
    el.value = null;
    return (T) result;
  }

  final boolean isEmpty() {
    return  (head == null);
  }

  @SuppressWarnings("unchecked") // This data structure should be re-thought
  final OPT_LinkedListObjectEnumerator<T> elements() {
    return  new OPT_LinkedListObjectEnumerator<T>
        ((OPT_LinkedListObjectElement<T>)head);
  }
}
