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
 * An <code>EnumerationIterator</code> converts an <code>Enumeration</code>
 * into an <code>Iterator</code>.
 */
public class EnumerationIterator<T> implements java.util.Iterator<T> {
  private final java.util.Enumeration<T> e;

  public EnumerationIterator(java.util.Enumeration<T> e) {
    this.e = e;
  }

  public boolean hasNext() {
    return e.hasMoreElements();
  }

  public T next() {
    return e.nextElement();
  }

  public void remove() {
    throw new java.lang.UnsupportedOperationException();
  }
}
