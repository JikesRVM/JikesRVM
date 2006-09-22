/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
