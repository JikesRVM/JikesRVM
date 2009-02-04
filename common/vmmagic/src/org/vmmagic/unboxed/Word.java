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
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;
import org.vmmagic.pragma.RawStorage;

/**
 * A generic pointer-sized integer. Can be converted to/from other pointer-sized types, and
 * provides shifting and masking operations.
 */
@Unboxed
@RawStorage(lengthInWords = true, length = 1)
public final class Word {

  /**
   * Convert an int to a word. On 64-bit machines, sign-extend the high order bit.
   *
   * @param val
   * @return A word instance whose value is val, sign-extended on 64 bit machines
   */
  public static Word fromIntSignExtend(int val) {
    return null;
  }

  /**
   * Convert an int to a word. On 64-bit machines, zero-extend the high order bit.
   *
   * @param val
   * @return A word instance whose value is val, zero-extended on 64 bit machines
   */
  public static Word fromIntZeroExtend(int val) {
    return null;
  }

  /**
   * Convert a long to a word. On 64-bit this is a no-op, on 32-bit the long is truncated.
   *
   * @param val
   * @return A word instance whose value is val on 32 bit machine this truncates the upper 32 bits.
   */
  public static Word fromLong(long val) {
    return null;
  }

  /**
   * The Word constant 0.
   * Equivalent to Word.fromIntSignExtend(0), but more readable.
   *
   * @return the Word constant 0.
   */
  public static Word zero() {
    return null;
  }

  /**
   * The Word constant 1.
   * Equivalent to Word.fromIntSignExtend(1), but more readable.
   *
   * @return the Word constant 1.
   */
  public static Word one() {
    return null;
  }

  /**
   * The maximum representable Word value.  Words are unsigned, so this is
   * a word full of 1s, 32/64-bit safe.
   *
   * @return the maximum representable Word value
   */
  public static Word max() {
    return null;
  }

  /**
   * Type-cast to an int, truncating on 64-bit platforms.
   *
   * @return an int, with the same value as the word on 32 bit platforms; truncates on 64 bit platforms.
   */
  public int toInt() {
    return 0;
  }

  /**
   * Type-cast to a long, zero-extending on a 32-bit platform.
   * @return a long, with the same value as the word (zero extends on 32 bit platforms).
   */
  public long toLong() {
    return 0L;
  }

  /** Type-cast to an address. */
  public Address toAddress() {
    return null;
  }

  /** Type-cast to an offset */
  public Offset toOffset() {
    return null;
  }

  /** Type-cast to an extent */
  public Extent toExtent() {
    return null;
  }

  /**
   * Add two words
   *
   * @param w2
   * @return The word whose value is this+w2
   */
  public Word plus(Word w2) {
    return null;
  }

  /**
   * Add an offset to a word
   * @param w2
   * @return The word whose value is this+w2
   */
  public Word plus(Offset w2) {
    return null;
  }

  /**
   * Add an extent to a word
   * @param w2
   * @return The word whose value is this+w2
   */
  public Word plus(Extent w2) {
    return null;
  }

  /**
   * Subtract two words
   * @param w2
   * @return The word whose value is this-w2
   */
  public Word minus(Word w2) {
    return null;
  }

  /**
   * Subtract an offset from a word
   * @param w2
   * @return The word whose value is this-w2
   */
  public Word minus(Offset w2) {
    return null;
  }

  /**
   * Subtract an extent from a word.
   * @param w2
   * @return The word whose value is this-w2
   */
  public Word minus(Extent w2) {
    return null;
  }

  /**
   * Test for zero.  Equivalent to .EQ(Word.zero())
   * @return return true if this is equal to Word.zero(), false otherwise
   */
  public boolean isZero() {
    return false;
  }

  /**
   * Test for zero.  Equivalent to .EQ(Word.max())
   * @return true if this is equal to Word.max(), false otherwise
   */
  public boolean isMax() {
    return false;
  }

  /**
   * Less-than comparison
   * @param addr2
   * @return true if this <code>Word</code> instance is <i>less than</i> <code>addr2</code>
   */
  public boolean LT(Word addr2) {
    return false;
  }

  /**
   * Less-than or equal comparison
   * @param w2
   * @return true if this <code>Word</code> instance is <i>less than or equal to</i> <code>w2</code>
   */
  public boolean LE(Word w2) {
    return false;
  }

  /**
   * Greater-than comparison
   * @param w2
   * @return true if this <code>Word</code> instance is <i>greater than</i> <code>w2</code>
   */
  public boolean GT(Word w2) {
    return false;
  }

  /**
   * Greater-than or equal comparison
   * @param w2
   * @return true if this <code>Word</code> instance is <i>greater than or equal to</i> <code>w2</code>
   */
  public boolean GE(Word w2) {
    return false;
  }

  /**
   * Equality comparison
   * @param w2
   * @return true if this <code>Word</code> instance is <i>equal to</i> <code>w2</code>
   */
  public boolean EQ(Word w2) {
    return false;
  }

  /**
   * Not-equal comparison
   * @param w2
   * @return true if this <code>Word</code> instance is <i>not equal to</i> <code>w2</code>
   */
  public boolean NE(Word w2) {
    return false;
  }

  /**
   * Bit-wise and of two words.
   * @param w2
   * @return The word whose value is the bitwise and of this and w2
   */
  public Word and(Word w2) {
    return null;
  }

  /**
   * Bit-wise or of two words.
   * @param w2
   * @return The word whose value is the bitwise not of this and w2
   */
  public Word or(Word w2) {
    return null;
  }

  /**
   * Bit-wise complement of a word.
   * @return the bitwise complement of this
   */
  public Word not() {
    return null;
  }

  /**
   * Bit-wise exclusive or of two words.
   * @param w2
   * @return The word whose value is the bitwise xor of this and w2
   */
  public Word xor(Word w2) {
    return null;
  }

  /**
   * Left-shift a word. Shifts of a size greater than the Word are undefined and
   * have an architecture and compiler specific behaviour. On Intel the shift
   * amount ignores the most significant bits, for example for a 32bit Word 1
   * &lt;&lt; 32 == 1, the result will be 0 on PowerPC. Shifts may or may not be
   * combined by the compiler, this yields differing behaviour, for example for a
   * 32bit Word 1 &lt;&lt;32 may or may not equal 1 &lt;&lt; 16 &lt;&lt; 16.
   *
   * @param amt the amount to shift by
   * @return new Word shifted by the given amount
   */
  public Word lsh(int amt) {
    return null;
  }

  /**
   * Logical right-shift a word. Shifts of a size greater than the Word are undefined and
   * have an architecture and compiler specific behaviour see also {@link #lsh(int)}.
   *
   * @param amt the amount to shift by
   * @return new Word shifted by the given amount
   */
  public Word rshl(int amt) {
    return null;
  }

  /**
   * Arithmetic right-shift a word. Shifts of a size greater than the Word are undefined and
   * have an architecture and compiler specific behaviour see also {@link #lsh(int)}.
   * Arithmetic right-shift a word.  Equivalent to the integer <code>&gt;&gt;</code> operator
   *
   * @param amt the amount to shift by
   * @return new Word shifted by the given amount
   */
  public Word rsha(int amt) {
    return null;
  }
}

