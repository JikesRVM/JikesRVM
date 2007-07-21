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

import org.jikesrvm.mm.mmtk.Barriers;
import org.jikesrvm.runtime.VM_Entrypoints;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.scheduler.VM_Synchronization;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 *  Various service utilities.  This is a common place for some shared utility routines
 */
@Uninterruptible
public class VM_Services {
  /**
   * Biggest buffer you would possibly need for {@link #dump(char[], int)}
   * Modify this if you modify that method.
   */
  public static final int MAX_DUMP_LEN =
    10 /* for thread ID  */ + 7 + 5 + 5 + 11 + 5 + 10 + 13 + 17 + 10;

  /** Pre-allocate the dump buffer, since dump() might get called inside GC. */
  private static final char[] dumpBuffer = new char[MAX_DUMP_LEN];

  @SuppressWarnings({"unused", "CanBeFinal", "UnusedDeclaration"})// accessed via VM_EntryPoints
  private static int dumpBufferLock = 0;

  /** Reset at boot time. */
  private static Offset dumpBufferLockOffset = Offset.max();

  /**
   * A map of hexadecimal digit values to their character representations.
   * <P>
   * XXX We currently only use '0' through '9'.  The rest are here pending
   * possibly merging this code with the similar code in Log.java, or breaking
   * this code out into a separate utility class.
   */
  private static final char [] hexDigitCharacter =
  { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e',
    'f' };

  /**
   * How many characters we need to have in a buffer for building string
   * representations of <code>long</code>s, such as {@link #intBuffer}. A
   * <code>long</code> is a signed 64-bit integer in the range -2^63 to
   * 2^63+1. The number of digits in the decimal representation of 2^63 is
   * ceiling(log10(2^63)) == ceiling(63 * log10(2)) == 19. An extra character
   * may be required for a minus sign (-). So the maximum number of characters
   * is 20.
   */
  private static final int INT_BUFFER_SIZE = 20;

  /** A buffer for building string representations of <code>long</code>s */
  private static final char [] intBuffer = new char[INT_BUFFER_SIZE];

  /** A lock for {@link #intBuffer} */
  @SuppressWarnings({"unused", "CanBeFinal", "UnusedDeclaration"})// accessed via VM_EntryPoints
  private static int intBufferLock = 0;

  /** The offset of {@link #intBufferLock} in this class's TIB.
   *  This is set properly at boot time, even though it's a
   *  <code>private</code> variable. . */
  private static Offset intBufferLockOffset = Offset.max();

  /**
   * Called during the boot sequence, any time before we go multi-threaded. We
   * do this so that we can leave the lockOffsets set to -1 until the VM
   * actually needs the locking (and is running multi-threaded).
   */
  public static void boot() {
    dumpBufferLockOffset = VM_Entrypoints.dumpBufferLockField.getOffset();
    intBufferLockOffset = VM_Entrypoints.intBufferLockField.getOffset();
  }

  public static char[] grabDumpBuffer() {
    if (!dumpBufferLockOffset.isMax()) {
      while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), dumpBufferLockOffset, 1)) {
        ;
      }
    }
    return dumpBuffer;
  }

  public static void releaseDumpBuffer() {
    if (!dumpBufferLockOffset.isMax()) {
      VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), dumpBufferLockOffset, 0);
    }
  }


  /** Copy a String into a character array.
   *
   *  This function may be called during GC and may be used in conjunction
   *  with the MMTk {@link org.mmtk.utility.Log} class.   It avoids write barriers and allocation.
   *  <p>
   *  XXX This function should probably be moved to a sensible location where
   *   we can use it as a utility.   Suggestions welcome.
   *  <P>
   *
   * @param dest char array to copy into.
   * @param destOffset Offset into <code>dest</code> where we start copying
   *
   * @return 1 plus the index of the last character written.  If we were to
   *         write zero characters (which we won't) then we would return
   *         <code>offset</code>.  This is intended to represent the first
   *         unused position in the array <code>dest</code>.  However, it also
   *         serves as a pseudo-overflow check:  It may have the value
   *         <code>dest.length</code>, if the array <code>dest</code> was
   *         completely filled by the call, or it may have a value greater
   *         than <code>dest.length</code>, if the info needs more than
   *         <code>dest.length - offset</code> characters of space.
   *
   * @return  -1 if <code>offset</code> is negative.
   *
   * the MMTk {@link org.mmtk.utility.Log} class).
   */
  public static int sprintf(char[] dest, int destOffset, String s) {
    final char[] sArray = java.lang.JikesRVMSupport.getBackingCharArray(s);
    return sprintf(dest, destOffset, sArray);
  }

  public static int sprintf(char[] dest, int destOffset, char[] src) {
    return sprintf(dest, destOffset, src, 0, src.length);
  }

  /** Copies characters from <code>src</code> into the destination character
   * array <code>dest</code>.
   *
   *  The first character to be copied is at index <code>srcBegin</code>; the
   *  last character to be copied is at index <code>srcEnd-1</code>.  (This is
   *  the same convention as followed by java.lang.String#getChars).
   *
   * @param dest char array to copy into.
   * @param destOffset Offset into <code>dest</code> where we start copying
   * @param src Char array to copy from
   * @param srcStart index of the first character of <code>src</code> to copy.
   * @param srcEnd index after the last character of <code>src</code> to copy.
   */
  public static int sprintf(char[] dest, int destOffset, char[] src, int srcStart, int srcEnd) {
    for (int i = srcStart; i < srcEnd; ++i) {
      char nextChar = Barriers.getArrayNoBarrierStatic(src, i);
      destOffset = sprintf(dest, destOffset, nextChar);
    }
    return destOffset;
  }

  public static int sprintf(char[] dest, int destOffset, char c) {
    if (destOffset < 0) {
      // bounds check
      return -1;
    }

    if (destOffset < dest.length) {
      Barriers.setArrayNoBarrierStatic(dest, destOffset, c);
    }
    return destOffset + 1;
  }

  /** Copy the printed decimal representation of a long into
   * a character array.  The value is not padded and no
   * thousands seperator is copied.  If the value is negative a
   * leading minus sign (-) is copied.
   *
   *  This function may be called during GC and may be used in conjunction
   *  with the Log class.   It avoids write barriers and allocation.
   *  <p>
   *  XXX This function should probably be moved to a sensible location where
   *   we can use it as a utility.   Suggestions welcome.
   * <p>
   *  XXX This method's implementation is stolen from the {@link org.mmtk.utility.Log} class.
   *
   * @param dest char array to copy into.
   * @param offset Offset into <code>dest</code> where we start copying
   *
   * @return 1 plus the index of the last character written.  If we were to
   *         write zero characters (which we won't) then we would return
   *         <code>offset</code>.  This is intended to represent the first
   *         unused position in the array <code>dest</code>.  However, it also
   *         serves as a pseudo-overflow check:  It may have the value
   *         <code>dest.length</code>, if the array <code>dest</code> was
   *         completely filled by the call, or it may have a value greater
   *         than <code>dest.length</code>, if the info needs more than
   *         <code>dest.length - offset</code> characters of space.
   *
   * @return  -1 if <code>offset</code> is negative.
   */
  public static int sprintf(char[] dest, int offset, long l) {
    boolean negative = l < 0;
    int nextDigit;
    char nextChar;
    int index = INT_BUFFER_SIZE - 1;
    char[] intBuffer = grabIntBuffer();

    nextDigit = (int) (l % 10);
    nextChar = Barriers.getArrayNoBarrierStatic(hexDigitCharacter, negative ? -nextDigit : nextDigit);
    Barriers.setArrayNoBarrierStatic(intBuffer, index--, nextChar);
    l = l / 10;

    while (l != 0) {
      nextDigit = (int) (l % 10);
      nextChar = Barriers.getArrayNoBarrierStatic(hexDigitCharacter, negative ? -nextDigit : nextDigit);
      Barriers.setArrayNoBarrierStatic(intBuffer, index--, nextChar);
      l = l / 10;
    }

    if (negative) {
      Barriers.setArrayNoBarrierStatic(intBuffer, index--, '-');
    }

    int newOffset = sprintf(dest, offset, intBuffer, index + 1, INT_BUFFER_SIZE);
    releaseIntBuffer();
    return newOffset;
  }

  /**
   * Get exclusive access to {@link #intBuffer}, the buffer for building
   * string representations of integers.
   */
  private static char[] grabIntBuffer() {
    if (!intBufferLockOffset.isMax()) {
      while (!VM_Synchronization.testAndSet(VM_Magic.getJTOC(), intBufferLockOffset, 1)) {
        ;
      }
    }
    return intBuffer;
  }

  /**
   * Release {@link #intBuffer}, the buffer for building string
   * representations of integers.
   */
  private static void releaseIntBuffer() {
    if (!intBufferLockOffset.isMax()) {
      VM_Synchronization.fetchAndStore(VM_Magic.getJTOC(), intBufferLockOffset, 0);
    }
  }

  /**
   * Utility printing function.
   * @param i
   * @param blank
   */
  @Interruptible
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

  static void println() { VM.sysWrite("\n"); }

  static void print(String s) { VM.sysWrite(s); }

  static void println(String s) {
    print(s);
    println();
  }

  static void print(int i) { VM.sysWrite(i); }

  static void println(int i) {
    print(i);
    println();
  }

  static void print(String s, int i) {
    print(s);
    print(i);
  }

  static void println(String s, int i) {
    print(s, i);
    println();
  }

  public static void percentage(int numerator, int denominator, String quantity) {
    print("\t");
    if (denominator > 0) {
      print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
    } else {
      print("0");
    }
    print("% of ");
    println(quantity);
  }

  static void percentage(long numerator, long denominator, String quantity) {
    print("\t");
    if (denominator > 0L) {
      print((int) ((((double) numerator) * 100.0) / ((double) denominator)));
    } else {
      print("0");
    }
    print("% of ");
    println(quantity);
  }
}
