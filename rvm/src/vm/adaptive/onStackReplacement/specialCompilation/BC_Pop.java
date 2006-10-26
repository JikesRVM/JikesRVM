/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.jikesrvm.OSR;
/**
 *  pop
 *
 * @author Feng Qian
 */
public class BC_Pop extends OSR_PseudoBytecode {
  public byte[] getBytes() {
    byte[] codes = new byte[1];
    codes[0] = 87;
    return codes;
  }

  public int getSize() {
    return 1;
  }

  public int stackChanges() {
        return -1;
  }

  public String toString() {
    return "Pop";
  }
}
