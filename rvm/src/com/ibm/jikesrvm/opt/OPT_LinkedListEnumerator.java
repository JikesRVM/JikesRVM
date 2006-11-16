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
