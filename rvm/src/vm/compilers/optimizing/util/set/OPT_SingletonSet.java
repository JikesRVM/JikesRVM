/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_SingletonSet extends java.util.AbstractSet {
  Object o;

  OPT_SingletonSet (Object o) {
    this.o = o;
  }

  public boolean contains (Object o) {
    return  this.o == o;
  }

  public int hashCode () {
    return  this.o.hashCode();
  }

  public java.util.Iterator iterator () {
    return  new OPT_SingletonIterator(o);
  }

  public int size () {
    return  1;
  }

  public Object[] toArray() {
    Object[] a = new Object[1];
    a[0] = o;
    return  a;
  }
}
