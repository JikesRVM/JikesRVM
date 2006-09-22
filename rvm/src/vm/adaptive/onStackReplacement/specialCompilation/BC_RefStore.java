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
 * BC_RefStore: astore, astore_<i> 
 * 
 * @author Feng Qian
 */

public class BC_RefStore extends OSR_PseudoBytecode {
  private int bsize; 
  private byte[] codes; 
  private int lnum;
  
  public BC_RefStore(int local) {
    this.lnum = local;

    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_astore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_astore, local);
    }
  }

  public byte[] getBytes() {
    return codes;
  }

  public int getSize() {
    return bsize;
  }

  public int stackChanges() {
        return -1;
  }

  public String toString() {
    return "astore "+this.lnum;
  }
}
