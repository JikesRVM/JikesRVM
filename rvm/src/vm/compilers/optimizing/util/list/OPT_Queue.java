/*
 * (C) Copyright IBM Corp. 2001
 */
import  java.util.Enumeration;


/**
 * put your documentation comment here
 */
final class OPT_Queue {
  OPT_LinkedListElement head;
  OPT_LinkedListElement tail;
  OPT_LinkedListObjectElement free;

  /**
   * put your documentation comment here
   */
  OPT_Queue () {
    // head = tail = free = null;
  }

  /**
   * put your documentation comment here
   * @param   Object e
   */
  OPT_Queue (Object e) {
    head = tail = new OPT_LinkedListObjectElement(e);
  }

  /**
   * put your documentation comment here
   * @param e
   * @return 
   */
  final Object insert (Object e) {
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

  /**
   * put your documentation comment here
   * @return 
   */
  final Object remove () {
    OPT_LinkedListObjectElement el = (OPT_LinkedListObjectElement)head;
    head = head.next;
    el.next = free;
    free = el;
    Object result = el.value;
    el.value = null;
    return  result;
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
  final OPT_LinkedListObjectEnumerator elements () {
    return  new OPT_LinkedListObjectEnumerator
        ((OPT_LinkedListObjectElement)head);
  }
}



