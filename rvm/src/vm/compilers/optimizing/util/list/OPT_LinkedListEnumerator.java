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
final class OPT_LinkedListEnumerator
    implements Enumeration {
  OPT_LinkedListElement curr;

  OPT_LinkedListEnumerator(OPT_LinkedListElement start) {
    curr = start;
  }

  public boolean hasMoreElements() {
    return  curr != null;
  }

  public Object nextElement() {
    try {
      OPT_LinkedListElement e = curr;
      curr = curr.next;
      return  e;
    } catch (NullPointerException e) {
      throw  new NoSuchElementException("LinkedListEnumerator");
    }
  }
}
