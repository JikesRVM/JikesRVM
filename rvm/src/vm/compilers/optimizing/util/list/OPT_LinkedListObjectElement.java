/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_LinkedListObjectElement extends OPT_LinkedListElement {
  Object value;

  Object getValue() {
    return  value;
  }

  OPT_LinkedListObjectElement(Object o) {
    value = o;
  }

  OPT_LinkedListObjectElement(Object o, OPT_LinkedListObjectElement rest) {
    value = o;
    next = rest;
  }

  static OPT_LinkedListObjectElement cons(Object o, 
      OPT_LinkedListObjectElement rest) {
    return  new OPT_LinkedListObjectElement(o, rest);
  }

  OPT_LinkedListObjectElement copyFrom () {
    OPT_LinkedListObjectElement from = this;
    OPT_LinkedListObjectElement to = new 
        OPT_LinkedListObjectElement(from.value);
    OPT_LinkedListObjectElement to_curr = to;
    for (;;) {
      from = (OPT_LinkedListObjectElement)from.next;
      if (from == null)
        return  to;
      OPT_LinkedListObjectElement to_next = 
          new OPT_LinkedListObjectElement(from.value);
      to_curr.next = to_next;
      to_curr = to_next;
    }
  }
}



