/*
 * (C) Copyright Australian National University. 2004
 */
//$Id$
package org.vmmagic.unboxed; 

/**
 * Commenting required
 * 
 * @author Daniel Frampton
 */
public final class Extent {

  /**
   * @deprecated
   */
  public static Extent fromInt(int address) {
    return null;
  }

  public static Extent fromIntSignExtend(int address) {
    return null;
  }

  public static Extent fromIntZeroExtend(int address) {
    return null;
  }

  public static Extent zero () {
    return null;
  }

  public static Extent one () {
    return null;
  }

  public static Extent max() {
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

  public Extent add (int byteSize) {
    return null;
  }

  public Extent sub (int byteSize) {
    return null;
  }

  public Extent add (Extent byteSize) {
    return null;
  }

  public Extent sub (Extent byteSize) {
    return null;
  }

  public boolean LT (Extent extent2) {
    return false;
  }

  public boolean LE (Extent extent2) {
    return false;
  }

  public boolean GT (Extent extent2) {
    return false;
  }

  public boolean GE (Extent extent2) {
    return false;
  }

  public boolean EQ (Extent extent2) {
    return false;
  }

  public boolean NE (Extent extent2) {
    return false;
  }
}

