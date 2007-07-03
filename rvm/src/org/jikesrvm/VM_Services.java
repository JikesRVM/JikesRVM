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
package org.jikesrvm;

import org.vmmagic.pragma.NoInline;

/**
 *  Various service utilities.  This is a common place for some shared utility routines
 */
public class VM_Services {

  /**
   * Utility printing function.
   * @param i
   * @param blank
   */
  public static String getHexString(int i, boolean blank) {
    StringBuilder buf = new StringBuilder(8);
    for (int j = 0; j < 8; j++, i <<= 4) {
      int n = i >>> 28;
      if (blank && (n == 0) && (j != 7)) {
        buf.append(' ');
      } else {
        buf.append(Character.forDigit(n, 16));
        blank = false;
      }
    }
    return buf.toString();
  }

  @NoInline
  public static void breakStub() {
  }
}
