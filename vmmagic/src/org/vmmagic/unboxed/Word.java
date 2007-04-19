/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 *(C) Copyright Australian National University, 2004.
 */
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;

/**
 * (Mistakes in) comments by Robin Garner
 * @author Daniel Frampton
 * @see Address
 */
@Unboxed
public final class Word {

  /**
   * Convert an into to a word.  On 64-bit machines, sign-extend the
   * high order bit.
   * 
   * @param val
   * @return
   */
  public static Word fromIntSignExtend(int val) {
    return null;
  }

  /**
   * Convert an int to a word.  On 64-bit machines, zero-extend the
   * high order bit.
   * 
   * @param val
   * @return
   */
  public static Word fromIntZeroExtend(int val) {
    return null;
  }

  /**
   * Convert a long to a word.  On 64-bit this is a no-op.
   * TODO document behaviour on 32-bit.  Truncate ?
   * 
   * @param val
   * @return
   */
  public static Word fromLong(long val) {
    return null;
  }
  
  /**
   * The Word constant 0.  
   * Equivalent to Word.fromIntSignExtend(0), but more readable.
   * 
   * @return
   */
  public static Word zero() {
    return null;
  }

  /**
   * The Word constant 1.  
   * Equivalent to Word.fromIntSignExtend(1), but more readable.
   * 
   * @return
   */
  public static Word one() {
    return null;
  }

  /**
   * The maximum representable Word value.  Words are unsigned, so this is
   * a word full of 1s, 32/64-bit safe.
   * 
   * @return
   */
  public static Word max() {
    return null;
  }

  /**
   * Type-cast to an int, truncating on 64-bit platforms.
   * 
   * @return
   */
  public int toInt() {
    return 0;
  }

  /**
   * Type-cast to a long, zero-extending on a 32-bit platform.
   * @return
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
   * @return
   */
  public Word plus(Word w2) {
    return null;
  }

  /**
   * Add an offset to a word
   * @param w2
   * @return
   */
  public Word plus(Offset w2) {
    return null;
  }

  /**
   * Add an extent to a word
   * @param w2
   * @return
   */
  public Word plus(Extent w2) {
    return null;
  }

  /**
   * Subtract two words
   * @param w2
   * @return
   */
  public Word minus(Word w2) {
    return null;
  }

  /** 
   * Subtract an offset from a word
   * @param w2
   * @return
   */
  public Word minus(Offset w2) {
    return null;
  }

  /**
   * Subtract an extent from a word.
   * @param w2
   * @return
   */
  public Word minus(Extent w2) {
    return null;
  }

  /**
   * Test for zero.  Equivalent to .EQ(Word.zero())
   * @return
   */
  public boolean isZero() {
    return false;
  }

  /**
   * Test for zero.  Equivalent to .EQ(Word.max())
   * @return
   */
  public boolean isMax() {
    return false;
  }

  /**
   * Less-than comparison
   * @param addr2
   * @return
   */
  public boolean LT(Word addr2) {
    return false;
  }

  /**
   * Less-than or equal comparison
   * @param addr2
   * @return
   */
  public boolean LE(Word w2) {
    return false;
  }

  /**
   * Greater-than comparison
   * @param addr2
   * @return
   */
  public boolean GT(Word w2) {
    return false;
  }

  /**
   * Greater-than or equal comparison
   * @param addr2
   * @return
   */
  public boolean GE(Word w2) {
    return false;
  }

  /**
   * Equality comparison
   * @param w2
   * @return
   */
  public boolean EQ(Word w2) {
    return false;
  }

  /**
   * Not-equal comparison
   * @param w2
   * @return
   */
  public boolean NE(Word w2) {
    return false;
  }

  /**
   * Bit-wise and of two words.
   * @param w2
   * @return
   */
  public Word and(Word w2) {
    return null;
  }

  /**
   * Bit-wise or of two words.
   * @param w2
   * @return
   */
  public Word or(Word w2) {
    return null;
  }

  /**
   * Bit-wise complement of a word.
   * @param w2
   * @return
   */
  public Word not() {
    return null;
  }

  /**
   * Bit-wise exclusive or of two words.
   * @param w2
   * @return
   */
  public Word xor(Word w2) {
    return null;
  }

  /**
   * Left-shift a word.  Equivalent to the integer <code>&lt;&lt;</code> operator
   * @param w2
   * @return
   */
  public Word lsh(int amt) {
    return null;
  }

  /**
   * Logical right-shift a word.  Equivalent to the integer <code>&gt;&gt;&gt;</code> operator
   * @param w2
   * @return
   */
  public Word rshl(int amt) {
    return null;
  }

  /**
   * Arithmetic right-shift a word.  Equivalent to the integer <code>&gt;&gt;</code> operator
   * @param w2
   * @return
   */
  public Word rsha(int amt) {
    return null;
  }
}

