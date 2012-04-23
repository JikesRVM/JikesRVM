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
package org.mmtk.harness.vm;

import org.vmmagic.pragma.Uninterruptible;

@Uninterruptible
public class Strings extends org.mmtk.vm.Strings {
  /**
   * Log a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  @Override
  public void write(char [] c, int len) {
    String x = new String(c, 0, len);
    System.err.print(x);
  }

  /**
   * Log a thread identifier and a message.
   *
   * @param c character array with message starting at index 0
   * @param len number of characters in message
   */
  @Override
  public void writeThreadId(char [] c, int len) {
    String x = new String(c, 0, len);
    System.err.print(Thread.currentThread().getId() + " : " + x);
  }

  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   * <p>
   * <b>TODO:</b> There are special memory management semantics here that
   * someone should document.
   *
   * @param src the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  @Override
  public int copyStringToChars(String src, char [] dst, int dstBegin, int dstEnd) {
    int count = 0;
    for (int i=0; i <src.length(); i++) {
      if (dstBegin > dstEnd) break;
      dst[dstBegin] = src.charAt(i);
      dstBegin++;
      count++;
    }
    return count;
  }
}
