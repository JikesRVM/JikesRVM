/*
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
