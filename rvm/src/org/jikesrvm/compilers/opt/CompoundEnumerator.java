/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import java.util.Enumeration;

/**
 */
public class CompoundEnumerator<T> implements Enumeration<T> {
  private final Enumeration<T> first;
  private final Enumeration<T> second;

  public CompoundEnumerator(Enumeration<T> first, Enumeration<T> second) {
    this.first = first;
    this.second = second;
  }

  public boolean hasMoreElements() {
    return first.hasMoreElements() || second.hasMoreElements();
  }

  public T nextElement() {
    if (first.hasMoreElements()) {
      return first.nextElement();
    } else {
      return second.nextElement();
    }
  }
}
