/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * checkcast instruction
 *
 * @author Feng Qian
 */
public class BC_CheckCast extends OSR_PseudoBytecode {
  private final static int bsize = 6;
  private final int tid;
  
  public BC_CheckCast(int typeId) {
    this.tid = typeId;
  }

  public byte[] getBytes() {
    byte[] codes = initBytes(bsize, PSEUDO_CheckCast);
    int2bytes(codes, 2, tid);
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return 0;
  }
 
  public String toString() {
    return "CheckCast "+this.tid;
  }
}
