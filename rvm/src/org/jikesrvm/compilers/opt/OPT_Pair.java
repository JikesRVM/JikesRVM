/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2004
 */
package org.jikesrvm.compilers.opt;

class OPT_Pair {
  final Object first;
  final Object second;

  /**
   * Constructor
   * @param    f  The first element in the pair.
   * @param    s  The second element in the pair.
   */
  OPT_Pair(Object f, Object s) {
    first = f;
    second = s;
  }

  public int hashCode() {
    return (first.hashCode() | second.hashCode());
  }

  public boolean equals(Object o) {
    return (o instanceof OPT_Pair) && first == ((OPT_Pair) o).first && second == ((OPT_Pair) o).second;
  }
}



