/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * An <code>IteratorEnumerator</code> converts an <code>Iterator</code>
 * into an <code>Enumeration</code>.
 *
 * @author Stephen Fink
 */
public class OPT_IteratorEnumerator
    implements java.util.Enumeration {
  private final java.util.Iterator i;

  public OPT_IteratorEnumerator(java.util.Iterator i) {
    this.i = i;
  }

  public boolean hasMoreElements() {
    return  i.hasNext();
  }

  public Object nextElement() {
    return  i.next();
  }
}
