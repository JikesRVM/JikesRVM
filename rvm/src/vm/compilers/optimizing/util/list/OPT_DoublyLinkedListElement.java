/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * An element in a doubly-linked list. It contains no fields. Subclass this
 * to add fields.
 *
 * @see DoublyLinkedList
 *
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
abstract class OPT_DoublyLinkedListElement {
  /**
   * Pointer to the previous element in the list.
   */
  OPT_DoublyLinkedListElement prev;
  /**
   * Pointer to the next element in the list.
   */
  OPT_DoublyLinkedListElement next;

  /**
   * Returns a pointer to the next element in the list, or null if this is the
   * last element.
   *
   * @return pointer to next element
   */
  final OPT_DoublyLinkedListElement getNext () {
    return  next;
  }

  final void setNext (OPT_DoublyLinkedListElement e) {
    next = e;
  }

  /**
   * Returns a pointer to the previous element in the list, or null if this is
   * the first element.
   *
   * @return pointer to previous element
   */
  final OPT_DoublyLinkedListElement getPrev () {
    return  prev;
  }

  /**
   * Call this method if prev/next fields of a 
   * OPT_DoublyLinkedListElement object
   * need to be explicitly cleared.
   */
  void clearDoublyLinkedListFields () {
    next = null;
    prev = null;
  }

  /**
   * Inserts the given element immediately after self in the list.
   * Returns the inserted element.
   *
   * @param elem element to insert
   * @return inserted element
   */
  final OPT_DoublyLinkedListElement insertAfter  (
      OPT_DoublyLinkedListElement elem) {
    OPT_DoublyLinkedListElement Next = next;
    if (Next != null)
      Next.prev = elem;
    elem.next = Next;
    elem.prev = this;
    next = elem;
    return  elem;
  }

  /**
   * Inserts the given element immediately before self in the list.
   *
   * @param elem element to insert
   */
  final void insertBefore (OPT_DoublyLinkedListElement elem) {
    OPT_DoublyLinkedListElement Prev = prev;
    if (Prev != null)
      Prev.next = elem;
    elem.prev = Prev;
    elem.next = this;
    prev = elem;
  }

  /**
   * Inserts the given element immediately after self in the list.
   *
   * @see insertAfter 
   * @param elem element to insert
   */
  final void insert (OPT_DoublyLinkedListElement elem) {
    insertAfter (elem);
  }

  /**
   * Appends the given doubly linked list to self.
   * It assumes that self has no successor.
   *
   * @param l list to append
   */
  final void append (OPT_DoublyLinkedListElement l) {
    //if (next != null) {
    // TODO: throw exception
    //}
    next = l;

    /*if (l != null)*/
    l.prev = this;
  }

  /**
   * Removes self from the list. Returns the next element in the list.
   *
   * @return next element in the list, after self
   */
  final OPT_DoublyLinkedListElement remove () {
    OPT_DoublyLinkedListElement Next = next, Prev = prev;
    if (Prev != null)
      Prev.next = Next;
    if (Next != null)
      Next.prev = Prev;
    return  Next;
  }

  /**
   * Returns the first element of the list that self is contained in.
   *
   * @return first element of the list
   */
  final OPT_DoublyLinkedListElement findFirst () {
    OPT_DoublyLinkedListElement p = this;
    while (p.prev != null) {
      p = p.prev;
    }
    return  p;
  }

  /**
   * Returns the last element of the list that self is contained in.
   *
   * @return last element of the list
   */
  final OPT_DoublyLinkedListElement findLast () {
    OPT_DoublyLinkedListElement p = this;
    while (p.next != null) {
      p = p.next;
    }
    return  p;
  }

  /**
   * Returns the length of the list from here to the end (including this one).
   *
   * @return length of remainder of list, inclusive.
   */
  final int lengthFront () {
    OPT_DoublyLinkedListElement p = this;
    int length = 1;
    while (p.next != null) {
      p = p.next;
      ++length;
    }
    return  length;
  }
}



