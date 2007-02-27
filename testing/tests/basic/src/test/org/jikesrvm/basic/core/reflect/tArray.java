/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package test.org.jikesrvm.basic.core.reflect;

import java.lang.reflect.Array;

/**
 * @author unascribed
 */
class tArray {
  private int i;

  tArray(int i) {
    this.i = i;
  }

  public String
  toString() {
    return "tArray " + i;
  }

  public static void main(String[] args) throws Exception {
    Class elementType = Class.forName("test.org.jikesrvm.basic.core.reflect.tArray");
    int length = 10;
    Object[] array = (Object[]) Array.newInstance(elementType, length);

    for (int i = 0, n = array.length; i < n; ++i)
      array[i] = new tArray(i);

    for (int i = 0, n = array.length; i < n; ++i)
      System.out.println(i + ": " + array[i]);
  }
}
