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
 * To be commented.
 * 
 * @author Daniel Frampton
 * @see Address
 */
@Unboxed
public final class Word {

  public static Word fromIntSignExtend(int val) {
    return null;
  }

  public static Word fromIntZeroExtend(int val) {
    return null;
  }

  public static Word fromLong(long val) {
    return null;
  }
  
  public static Word zero() {
    return null;
  }

  public static Word one() {
    return null;
  }

  public static Word max() {
    return null;
  }

  public int toInt() {
    return 0;
  }

  public long toLong() {
    return 0L;
  }

  public Address toAddress() {
    return null;
  }

  public Offset toOffset() {
    return null;
  }

  public Extent toExtent() {
    return null;
  }

  public Word plus(Word w2) {
    return null;
  }

  public Word plus(Offset w2) {
    return null;
  }

  public Word plus(Extent w2) {
    return null;
  }

  public Word minus(Word w2) {
    return null;
  }

  public Word minus(Offset w2) {
    return null;
  }

  public Word minus(Extent w2) {
    return null;
  }

  public boolean isZero() {
    return false;
  }

  public boolean isMax() {
    return false;
  }

  public boolean LT(Word addr2) {
    return false;
  }

  public boolean LE(Word w2) {
    return false;
  }

  public boolean GT(Word w2) {
    return false;
  }

  public boolean GE(Word w2) {
    return false;
  }

  public boolean EQ(Word w2) {
    return false;
  }

  public boolean NE(Word w2) {
    return false;
  }

  public Word and(Word w2) {
    return null;
  }

  public Word or(Word w2) {
    return null;
  }

  public Word not() {
    return null;
  }

  public Word xor(Word w2) {
    return null;
  }

  public Word lsh(int amt) {
    return null;
  }

  public Word rshl(int amt) {
    return null;
  }

  public Word rsha(int amt) {
    return null;
  }
}

