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
final class OPT_LinkedListObjectEnumerator<T>
    implements Enumeration<T> {
  OPT_LinkedListObjectElement<T> curr;

  OPT_LinkedListObjectEnumerator(OPT_LinkedListObjectElement<T> start) {
    curr = start;
  }

  public boolean hasMoreElements() {
    return  curr != null;
  }

  public T nextElement() {
    return  next();
  }

  public T next() {
    try {
      OPT_LinkedListObjectElement<T> e = curr;
      curr = curr.nextElement();
      return  e.value;
    } catch (NullPointerException e) {
      throw  new NoSuchElementException("OPT_LinkedListObjectEnumerator");
    }
  }
}
