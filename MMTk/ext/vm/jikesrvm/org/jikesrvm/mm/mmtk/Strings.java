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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.VM;
import org.jikesrvm.Services;
import org.jikesrvm.scheduler.RVMThread;

import org.vmmagic.pragma.*;

@Uninterruptible
public final class Strings extends org.mmtk.vm.Strings {

  @Override
  public void write(char [] c, int len) {
    VM.sysWrite(c, len);
  }

  @Override
  public void writeThreadId(char [] c, int len) {
    VM.tsysWrite(c, len);
  }

  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   *
   * @param str the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the desination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  @Override
  public int copyStringToChars(String str, char [] dst,
                               int dstBegin, int dstEnd) {
    if (!VM.runningVM)
      return naiveCopyStringToChars(str, dst, dstBegin, dstEnd);
    else
      return safeCopyStringToChars(str, dst, dstBegin, dstEnd);
  }

  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   * <p>
   * <b>TODO:</b> There are special memory management semantics here that
   * someone should document.
   *
   * @param str the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the destination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  private int safeCopyStringToChars(String str, char [] dst,
                                    int dstBegin, int dstEnd) {
    if (VM.VerifyAssertions) VM._assert(VM.runningVM);
    // FIXME Why do we need to disable thread switching here, in uninterruptible code??
    RVMThread.getCurrentThread().disableYieldpoints();
    char[] str_backing = java.lang.JikesRVMSupport.getBackingCharArray(str);
    int str_length = java.lang.JikesRVMSupport.getStringLength(str);
    int str_offset = java.lang.JikesRVMSupport.getStringOffset(str);
    int n = (dstBegin + str_length <= dstEnd) ? str_length : (dstEnd - dstBegin);
    for (int i = 0; i < n; i++) {
      Services.setArrayNoBarrier(dst, dstBegin + i, str_backing[str_offset+i]);
    }
    RVMThread.getCurrentThread().enableYieldpoints();
    return n;
  }
  /**
   * Copies characters from the string into the character array.
   * Thread switching is disabled during this method's execution.
   *
   * @param str the source string
   * @param dst the destination array
   * @param dstBegin the start offset in the destination array
   * @param dstEnd the index after the last character in the
   * destination to copy to
   * @return the number of characters copied.
   */
  @UninterruptibleNoWarn
  private int naiveCopyStringToChars(String str, char [] dst,
                                     int dstBegin, int dstEnd) {
    if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
    int len = str.length();
    int n = (dstBegin + len <= dstEnd) ? len : (dstEnd - dstBegin);
    for (int i = 0; i < n; i++)
      Services.setArrayNoBarrier(dst, dstBegin + i, str.charAt(i));
    return n;
  }
}
