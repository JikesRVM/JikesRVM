/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.opt;

/**
 * Reverse the order of an enumeration.
 */

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.NoSuchElementException;

public final class OPT_ReverseEnumerator<T> implements Enumeration<T> {

  private final ArrayList<T> vec;
  private int index;

  public boolean hasMoreElements() {
    return index > 0;
  }

  public T nextElement() {
    index--;
    if (index >= 0) {
      return vec.get(index);
    } else {
      throw new NoSuchElementException();
    }
  }

  public OPT_ReverseEnumerator(Enumeration<T> e) {
    vec = new ArrayList<T>();
    while (e.hasMoreElements()) {
      vec.add(e.nextElement());
    }
    index = vec.size();
  }
}



