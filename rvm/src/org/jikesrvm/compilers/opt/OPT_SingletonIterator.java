/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import java.util.Iterator;

/**
 */
class OPT_SingletonIterator<T> implements Iterator<T> {

  OPT_SingletonIterator (T o) {
    item = o;
    not_done = true;
  }
  boolean not_done;
  T item;

  public boolean hasNext () {
    return  not_done;
  }

  public T next () {
    if (not_done) {
      not_done = false;
      return  item;
    }
    throw  new java.util.NoSuchElementException();
  }

  public void remove () {
    throw  new java.lang.UnsupportedOperationException();
  }
}
