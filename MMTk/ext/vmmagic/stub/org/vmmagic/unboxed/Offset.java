/*
 * (C) Copyright Australian National University. 2004
 */
//$Id$
package org.vmmagic.unboxed;

/**
 * To be commented
 *
 * @author Daniel Frampton
 */
public final class Offset {

  /**
   * To be deprecated as soon as we find a good alternative
   */
  public static Offset fromInt(int address) {
    return null;
  }

  public static Offset fromIntSignExtend(int address) {
    return null;
  }

  public static Offset fromIntZeroExtend(int address) {
    return null;
  }

  public static Offset zero () {
    return null;
  }

  public static Offset max() {
    return null;
  }

  public int toInt () {
    return 0;
  }

  public long toLong () {
    return 0L;
  }

  public Word toWord() {
    return null;
  }

  public Offset add (int byteSize) {
    return null;
  }

  public Offset sub (int byteSize) {
    return null;
  }

  public Offset sub (Offset off2) {
    return null;
  }

  public boolean EQ (Offset off2) {
    return false;
  }

  public boolean NE (Offset off2) {
    return false;
  }

  public boolean sLT (Offset off2) {
    return false;
  }

  public boolean sLE (Offset off2) {
    return false;
  }

  public boolean sGT (Offset off2) {
    return false;
  }

  public boolean sGE (Offset off2) {
    return false;
  }

  public boolean isZero() {
    return false;
  }

  public boolean isMax() {
    return false;
  }
}

