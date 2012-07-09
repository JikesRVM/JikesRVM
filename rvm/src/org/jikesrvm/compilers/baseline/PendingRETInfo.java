/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.VM;

/**
 * This class is used during the processing of reference maps for a method.
 * <p>
 * When a JSR has been processed the processing of a RET is pending.
 * Need to track which JSR was processed, and where the "return address" value
 * is being held (i.e. is it on the operand stack, or in a local variable).
 * The value starts on the top of the stack, but is usually quickly moved to
 * a local variable.
 */
final class PendingRETInfo {

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

  public PendingRETInfo(int JSRSubStartByteIndex, int JSRBBNum, int returnAddressLocation, short JSRNextBBNum) {
    this.JSRSubStartByteIndex = JSRSubStartByteIndex;
    this.JSRBBNum = JSRBBNum;
    this.returnAddressLocation = returnAddressLocation;
    this.JSRNextBBNum = JSRNextBBNum;
    updatedOnce = false;
  }

  public PendingRETInfo(PendingRETInfo copyfrom) {
    this.JSRSubStartByteIndex = copyfrom.JSRSubStartByteIndex;
    this.JSRBBNum = copyfrom.JSRBBNum;
    this.returnAddressLocation = copyfrom.returnAddressLocation;
    this.JSRNextBBNum = copyfrom.JSRNextBBNum;
    this.updatedOnce = copyfrom.updatedOnce;
  }

  // ------------------ Instance Method ---------------------------

  public void updateReturnAddressLocation(int newLocation) {
    if (VM.VerifyAssertions) VM._assert(!updatedOnce);
    updatedOnce = true;
    returnAddressLocation = newLocation;
  }

}
