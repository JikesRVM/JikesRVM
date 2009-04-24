/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.util;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * A <code>FilterEnumerator</code> filters and maps a source
 * <code>Enumeration</code> to generate a new one.
 */
public class FilterEnumerator<S, T> implements Enumeration<T> {
  final Enumeration<S> e;
  final Filter<S, T> f;
  private S next;
  private boolean done;

  public FilterEnumerator(Enumeration<S> e, Filter<S, T> f) {
    this.e = e;
    this.f = f;
    advance();
  }

  private void advance() {
    while (e.hasMoreElements()) {
      next = e.nextElement();
      if (f.isElement(next)) {
        return;
      }
    }
    done = true;
  }

  public T nextElement() {
    if (done) {
      throw new NoSuchElementException();
    }
    S o = next;
    advance();
    return f.map(o);
  }

  public boolean hasMoreElements() {
    return !done;
  }

  public static class Filter<S, T> {                  // override with your mapping.

    public boolean isElement(S o) {
      return true;
    }

    @SuppressWarnings("unchecked")
    public T map(S o) {
      return (T) o;
    }
  }
}
