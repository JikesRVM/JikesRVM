/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
class OPT_SingletonIterator
    implements java.util.Iterator {

  OPT_SingletonIterator (Object o) {
    item = o;
    not_done = true;
  }
  boolean not_done;
  Object item;

  public boolean hasNext () {
    return  not_done;
  }

  public Object next () {
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
