/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_LinkedListObjectEnumerator
    implements Enumeration {
  OPT_LinkedListElement curr;

  OPT_LinkedListObjectEnumerator(OPT_LinkedListObjectElement start) {
    curr = start;
  }

  public boolean hasMoreElements() {
    return  curr != null;
  }

  public Object nextElement() {
    return  next();
  }

  public Object next() {
    try {
      OPT_LinkedListObjectElement e = (OPT_LinkedListObjectElement)curr;
      curr = curr.next;
      return  e.value;
    } catch (NullPointerException e) {
      throw  new NoSuchElementException("OPT_LinkedListObjectEnumerator");
    }
  }
}
