/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * A <code>FilterEnumerator</code> filters and maps a source
 * <code>Enumeration</code> to generate a new one.
 *
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
public class OPT_FilterEnumerator
    implements Enumeration {
  final Enumeration e;
  final Filter f;
  private Object next;
  private boolean done;

  public OPT_FilterEnumerator (Enumeration e, Filter f) {
    this.e = e;
    this.f = f;
    advance();
  }

  private void advance () {
    while (e.hasMoreElements()) {
      next = e.nextElement();
      if (f.isElement(next))
        return;
    }
    done = true;
  }

  public Object nextElement () {
    if (done)
      throw  new NoSuchElementException();
    Object o = next;
    advance();
    return  f.map(o);
  }

  public boolean hasMoreElements () {
    return  !done;
  }

  public static class Filter {                  // override with your mapping.

    public boolean isElement (Object o) {
      return  true;
    }

    public Object map (Object o) {
      return  o;
    }
  }
}
