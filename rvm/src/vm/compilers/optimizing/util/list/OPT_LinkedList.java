/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.VM;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
public class OPT_LinkedList {
  
  public OPT_LinkedList() { }

  public OPT_LinkedList(OPT_LinkedListElement e) {
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
    if (e == null) return;
    if (end != null) {
      end.insertAfter(e);
    } else {
      if (VM.VerifyAssertions) VM._assert(start == null); // empty list!
      start = e;
    }
    end = e;
  }

  /**
   * insert at the start of the list
   */
  final public void prepend(OPT_LinkedListElement e) {
    if (start != null) {
      e.next = start;
    } else {      // empty list
      if (VM.VerifyAssertions) VM._assert(end == null); // empty list!
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
      end = e;
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
    if (start == null) end = null; // list is now empty
    result.next = null;
    return  result;
  }

  private OPT_LinkedListElement start;
  private OPT_LinkedListElement end;
}



