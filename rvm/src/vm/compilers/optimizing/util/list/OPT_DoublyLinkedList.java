/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_DoublyLinkedList {
  OPT_DoublyLinkedListElement start;
  OPT_DoublyLinkedListElement end;

  OPT_DoublyLinkedList () {
  }

  OPT_DoublyLinkedList (OPT_DoublyLinkedListElement e) {
    start = end = e;
  }

  OPT_DoublyLinkedList (OPT_DoublyLinkedListElement s, 
      OPT_DoublyLinkedListElement e) {
    start = s;
    end = e;
  }

  final OPT_DoublyLinkedListElement first () {
    return  start;
  }

  final OPT_DoublyLinkedListElement last () {
    return  end;
  }

  final void remove (OPT_DoublyLinkedListElement e) {
    if (e == start) {
      if (e == end) {
        start = end = null;
      } 
      else {
        start = e.next;
        start.prev = null;
      }
    } 
    else if (e == end) {
      end = e.prev;
      end.next = null;
    } 
    else {
      e.remove();
    }
  }

  final OPT_DoublyLinkedListElement removeLast () {
    OPT_DoublyLinkedListElement e = end;
    if (e == start) {
      start = e = null;
    } 
    else {
      e = e.prev;
      e.next = null;
    }
    end = e;
    return  e;
  }

  // append at the end of the list
  final void 
  /*OPT_DoublyLinkedListElement*/
  append (OPT_DoublyLinkedListElement e) {
    OPT_DoublyLinkedListElement End = end;
    if (End != null) {
      End.append(e);
    } 
    else {
      start = e;
    }
    end = e;
    //return e;
  }

  // insert at the start of the list
  final void 
  /* OPT_DoublyLinkedListElement*/
  insert (OPT_DoublyLinkedListElement e) {
    OPT_DoublyLinkedListElement Start = start;
    if (Start != null) {
      Start.insertBefore(e);
    } 
    else {
      end = e;
    }
    start = e;
    //return e;
  }

  final void deleteAll () {
    start = end = null;
  }
}
