/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 *(C) Copyright Australian National University. 2004
 */
package org.vmmagic.unboxed;

import org.vmmagic.Unboxed;

/**
 * To be commented
 * 
 * @author Daniel Frampton
 */
@Unboxed
public final class Offset {

  public static Offset fromIntSignExtend(int address) {
    return null;
  }

  public static Offset fromIntZeroExtend(int address) {
    return null;
  }

  public static Offset zero() {
    return null;
  }

  public static Offset max() {
    return null;
  }

  public int toInt() {
    return 0;
  }

  public long toLong() {
    return 0L;
  }

  public Word toWord() {
    return null;
  }

  public Offset plus(int byteSize) {
    return null;
  }

  public Offset minus(int byteSize) {
    return null;
  }

  public Offset minus(Offset off2) {
    return null;
  }

  public boolean EQ(Offset off2) {
    return false;
  }

  public boolean NE(Offset off2) {
    return false;
  }

  public boolean sLT(Offset off2) {
    return false;
  }

  public boolean sLE(Offset off2) {
    return false;
  }

  public boolean sGT(Offset off2) {
    return false;
  }

  public boolean sGE(Offset off2) {
    return false;
  }

  public boolean isZero() {
    return false;
  }

  public boolean isMax() {
    return false;
  }
}

