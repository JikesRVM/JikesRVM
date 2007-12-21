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
package org.jikesrvm.compilers.opt.util;

public final class Pair {
  public final Object first;
  public final Object second;

  /**
   * Constructor
   * @param    f  The first element in the pair.
   * @param    s  The second element in the pair.
   */
  public Pair(Object f, Object s) {
    first = f;
    second = s;
  }

  public int hashCode() {
    return (first.hashCode() | second.hashCode());
  }

  public boolean equals(Object o) {
    return (o instanceof Pair) && first == ((Pair) o).first && second == ((Pair) o).second;
  }
}



