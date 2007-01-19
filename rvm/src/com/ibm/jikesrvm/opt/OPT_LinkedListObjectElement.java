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
final class OPT_LinkedListObjectElement<T> extends OPT_LinkedListElement {
  T value;

  T getValue() {
    return  value;
  }

  OPT_LinkedListObjectElement(T o) {
    value = o;
  }

  OPT_LinkedListObjectElement(T o, OPT_LinkedListObjectElement<T> rest) {
    value = o;
    next = rest;
  }
  
  @SuppressWarnings("unchecked")
  OPT_LinkedListObjectElement<T> nextElement() {
    return (OPT_LinkedListObjectElement<T>)next;
  }

  static <U> OPT_LinkedListObjectElement<U> cons(U o, 
      OPT_LinkedListObjectElement<U> rest) {
    return  new OPT_LinkedListObjectElement<U>(o, rest);
  }

  OPT_LinkedListObjectElement<T> copyFrom () {
    OPT_LinkedListObjectElement<T> from = this;
    OPT_LinkedListObjectElement<T> to = 
      new OPT_LinkedListObjectElement<T>(from.value);
    OPT_LinkedListObjectElement<T> to_curr = to;
    for (;;) {
      from = from.nextElement();
      if (from == null)
        return  to;
      OPT_LinkedListObjectElement<T> to_next = 
          new OPT_LinkedListObjectElement<T>(from.value);
      to_curr.next = to_next;
      to_curr = to_next;
    }
  }
}



