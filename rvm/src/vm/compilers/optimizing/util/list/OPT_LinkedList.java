/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_LinkedList {

  OPT_LinkedList() {
  }

  OPT_LinkedList(OPT_LinkedListElement e) {
    start = end = e;
  }

  final public OPT_LinkedListElement first() {
    return start;
  }

  final public OPT_LinkedListElement last() {
    return  end;
  }

  /**
   * append at the end of the list
   */
  final public void append(OPT_LinkedListElement e) {
    if (e == null)
      return;
    OPT_LinkedListElement End = end;
    if (End != null) {
      End.insertAfter(e);
    } 
    else {      // empty list
      start = e;
    }
    end = e;
  }

  /**
   * insert at the start of the list
   */
  final public void prepend(OPT_LinkedListElement e) {
    OPT_LinkedListElement Start = start;
    if (Start != null) {
      e.next = Start;
    } 
    else {      // empty list
      end = e;
    }
    // in either case, e is the first node on the list
    start = e;
  }

  /**
   * removes the next element from the list
   */
  final public void removeNext(OPT_LinkedListElement e) {
    // update end if needed
    if (end == e.getNext())
      end = null;
    // remove the element
    e.next = e.getNext().getNext();
  }

  /**
   * remove an element from the list.
   */
  final public void remove(OPT_LinkedListElement e) {
    if (start == e) {
      removeHead();
    } else {
      if (start == null) return;
      OPT_LinkedListElement current = start;
      OPT_LinkedListElement next = start.next;
      while (next != null) {
        if (next == e) {
          removeNext(current);
          return;
        } else {
          current = next;
          next = current.next;
        }
      }
    }
  }

  /**
   * removes the head element from the list
   */
  final public OPT_LinkedListElement removeHead() {
    if (start == null)
      return  null;
    OPT_LinkedListElement result = start;
    start = result.next;
    result.next = null;
    return  result;
  }
  private OPT_LinkedListElement start;
  private OPT_LinkedListElement end;
}



