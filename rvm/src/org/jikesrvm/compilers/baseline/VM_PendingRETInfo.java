/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.VM;

/**
 * This class is used during the processing of reference maps for a method.
 *
 * When a JSR has been processed the processing of a RET is pending. 
 * Need to track which JSR was processed, and where the "return address" value
 * is being held (ie is it on the operand stack, or in a local variable). 
 * The value starts on the top of the stack, but is usually quickly moved to 
 * a local variable. 
 */
public final class VM_PendingRETInfo {

  // --------------------- Instance Data -------------------

  public final int JSRSubStartByteIndex;
  public final int JSRBBNum;
  /**
   * index into map - represents either a local variable or a stack
   * position
   */
  public int returnAddressLocation;
  /** Sanity check the return address location is only updated once */
  private boolean updatedOnce;
  /** Block number of block after JSR */
  public final short JSRNextBBNum;

  // --------------------- Constructors ----------------------------

  public VM_PendingRETInfo(int JSRSubStartByteIndex, int JSRBBNum, 
                           int returnAddressLocation, short JSRNextBBNum) {
    this.JSRSubStartByteIndex  = JSRSubStartByteIndex;
    this.JSRBBNum              = JSRBBNum;
    this.returnAddressLocation = returnAddressLocation;
    this.JSRNextBBNum          = JSRNextBBNum;
    updatedOnce                = false;
  }

  public VM_PendingRETInfo(VM_PendingRETInfo copyfrom) {
    this.JSRSubStartByteIndex  = copyfrom.JSRSubStartByteIndex;
    this.JSRBBNum              = copyfrom.JSRBBNum;
    this.returnAddressLocation = copyfrom.returnAddressLocation;
    this.JSRNextBBNum          = copyfrom.JSRNextBBNum;
    this.updatedOnce           = copyfrom.updatedOnce;
  }

  // ------------------ Instance Method ---------------------------

  public void updateReturnAddressLocation(int newLocation) {
    if (VM.VerifyAssertions) VM._assert(!updatedOnce);
    updatedOnce = true;
    returnAddressLocation = newLocation;
  }

}
