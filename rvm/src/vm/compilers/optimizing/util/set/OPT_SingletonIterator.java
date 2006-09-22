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
