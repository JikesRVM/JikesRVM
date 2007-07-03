/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import java.util.Iterator;

class OPT_SingletonIterator<T> implements Iterator<T> {

  OPT_SingletonIterator(T o) {
    item = o;
    not_done = true;
  }

  boolean not_done;
  T item;

  public boolean hasNext() {
    return not_done;
  }

  public T next() {
    if (not_done) {
      not_done = false;
      return item;
    }
    throw new java.util.NoSuchElementException();
  }

  public void remove() {
    throw new java.lang.UnsupportedOperationException();
  }
}
