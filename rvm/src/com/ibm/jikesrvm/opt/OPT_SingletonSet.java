/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.opt;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_SingletonSet<T> extends java.util.AbstractSet<T> {
  T o;

  OPT_SingletonSet (T o) {
    this.o = o;
  }

  public boolean contains (Object o) {
    return  this.o == o;
  }

  public int hashCode () {
    return  this.o.hashCode();
  }

  public java.util.Iterator<T> iterator () {
    return  new OPT_SingletonIterator<T>(o);
  }

  public int size () {
    return  1;
  }

  public T[] toArray() {
    Object[] a = new Object[1];
    a[0] = o;
    @SuppressWarnings("unchecked") // Well known problem of generic arrays
    T[] result = (T[])a;
    return  result;
  }
}
