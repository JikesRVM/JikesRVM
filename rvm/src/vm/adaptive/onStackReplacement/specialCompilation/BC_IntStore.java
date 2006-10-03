/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002
 */
//$Id$

package com.ibm.JikesRVM.OSR;
/**
 * BC_IntStore : istore_<?>, istore 
 *
 *      Local number            Instruction
 *      [0, 3]                  istore_<i>
 *      other                   istore, wide istore
 *
 * @author Feng Qian
 */
public class BC_IntStore extends OSR_PseudoBytecode {
  private int bsize;
  private byte[] codes;
  private int lnum;

  public BC_IntStore(int local){
    this.lnum = local;
    if (local <= 255) {
      bsize = 2;
      codes = makeOUcode(JBC_istore, local);
    } else {
      bsize = 4;
      codes = makeWOUUcode(JBC_istore, local);
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
    return "istore "+lnum;
  }
}
