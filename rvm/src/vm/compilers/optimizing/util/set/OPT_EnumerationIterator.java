/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

/**
 * An <code>OPT_EnumerationIterator</code> converts an <code>Enumeration</code>
 * into an <code>Iterator</code>.
 *
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
public class OPT_EnumerationIterator
    implements java.util.Iterator {
  private final java.util.Enumeration e;

  public OPT_EnumerationIterator (java.util.Enumeration e) {
    this.e = e;
  }

  public boolean hasNext () {
    return  e.hasMoreElements();
  }

  public Object next () {
    return  e.nextElement();
  }

  public void remove () {
    throw  new java.lang.UnsupportedOperationException();
  }
}
