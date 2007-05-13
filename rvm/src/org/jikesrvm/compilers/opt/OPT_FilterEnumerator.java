/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * A <code>FilterEnumerator</code> filters and maps a source
 * <code>Enumeration</code> to generate a new one.
 *
 */
public class OPT_FilterEnumerator<S,T>
    implements Enumeration<T> {
  final Enumeration<S> e;
  final Filter<S,T> f;
  private S next;
  private boolean done;

  public OPT_FilterEnumerator (Enumeration<S> e, Filter<S,T> f) {
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

  public T nextElement () {
    if (done)
      throw  new NoSuchElementException();
    S o = next;
    advance();
    return  f.map(o);
  }

  public boolean hasMoreElements () {
    return  !done;
  }

  public static class Filter<S,T> {                  // override with your mapping.

    public boolean isElement (S o) {
      return  true;
    }

    @SuppressWarnings("unchecked")
    public T map (S o) {
      return  (T)o;
    }
  }
}
