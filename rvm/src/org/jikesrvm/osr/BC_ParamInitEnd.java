/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */

package org.jikesrvm.osr;
/**
 *  LocalInitEnd
 *
 */
public class BC_ParamInitEnd extends OSR_PseudoBytecode {
  public byte[] getBytes() {
        return initBytes(2, PSEUDO_ParamInitEnd);
  }

  public int getSize() {
    return 2;
  }

  public int stackChanges() {
        return 0;
  }

  public String toString() {
    return "ParamInitEnd";
  }
}
