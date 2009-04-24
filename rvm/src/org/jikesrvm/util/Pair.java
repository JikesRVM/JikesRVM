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

package org.jikesrvm.util;

public class Pair<X, Y> {
  public final X first;
  public final Y second;

  public Pair(X f, Y s) {
    first = f;
    second = s;
  }

  public int hashCode() {
    return (first.hashCode() | second.hashCode());
  }

  public boolean equals(Object o) {
    if (o instanceof Pair<?, ?>) {
      Pair<?, ?> p = (Pair<?, ?>) o;
      return (first == p.first) && (second == p.second);
    } else {
      return false;
    }
  }
}
