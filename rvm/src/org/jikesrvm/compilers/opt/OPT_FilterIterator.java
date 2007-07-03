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

/**
 * A <code>FilterIterator</code> filters and maps a source
 * <code>Iterator</code> to generate a new one.
 */
public class OPT_FilterIterator<T> implements java.util.Iterator<T> {
  final java.util.Iterator<T> i;
  final Filter<T> f;
  private T next = null;
  private boolean done = false;

  public OPT_FilterIterator(java.util.Iterator<T> i, Filter<T> f) {
    this.i = i;
    this.f = f;
    advance();
  }

  private void advance() {
    while (i.hasNext()) {
      next = i.next();
      if (f.isElement(next)) {
        return;
      }
    }
    done = true;
  }

  public T next() {
    if (done) {
      throw new java.util.NoSuchElementException();
    }
    T o = next;
    advance();
    return f.map(o);
  }

  public boolean hasNext() {
    return !done;
  }

  public void remove() {
    throw new java.lang.UnsupportedOperationException();
  }

  public static class Filter<T> {                  // override with your mapping.

    public boolean isElement(Object o) {
      return true;
    }

    public T map(T o) {
      return o;
    }
  }
}
