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
import org.vmmagic.pragma.Uninterruptible;

/**
 * Unusual maps are maps to track references that don't take the usual format.<p>
 * Currently unusual maps include:
 *    maps of locations within JSR subroutines (includes return address map).
 * In the future the return address maps may be expanded to include other
 * internal pointers or internal/external pointers may be handled separately.
 */
@Uninterruptible
final class UnusualMaps {

  /** For maps of JSR subroutine locations index into the normal reference map of where the return address can be located */
  int returnAddressIndex;
  /** index into the map table of the references set map */
  int referenceMapIndex;
  /** index into the map table of the non-reference set map */
  int nonReferenceMapIndex;
  /** index into the map table of the return address map */
  int returnAddressMapIndex;
  /** index into the array of normal maps ie the back-pointer */
  int normalMapIndex;

  /** set the index in the stack frame of the return address for this map */
  void setReturnAddressIndex(int index) {
    returnAddressIndex = index;
  }

  /** provide the index in the stack frame of the return address for this map */
  int getReturnAddressIndex() {
    return returnAddressIndex;
  }

  /** set the  offset of the reference map in the stackmap list of maps */
  void setReferenceMapIndex(int index) {
    referenceMapIndex = index;
  }

  /** provide the index in the stackmaps for the reference map */
  int getReferenceMapIndex() {
    return referenceMapIndex;
  }

  /** set the  offset of the non-reference map in the stackmap list of maps */
  void setNonReferenceMapIndex(int index) {
    nonReferenceMapIndex = index;
  }

  /** provide the index in the stackmaps for the non-reference map */
  int getNonReferenceMapIndex() {
    return nonReferenceMapIndex;
  }

  /** set the  offset of the returnAddress map in the stackmap list of maps */
  void setReturnAddressMapIndex(int index) {
    returnAddressMapIndex = index;
  }

  /** provide the index in the stackmaps for the return Address map */
  int getReturnAddressMapIndex() {
    return returnAddressMapIndex;
  }

  /** provide the normal map index ie the back-pointer */
  int getNormalMapIndex() {
    return normalMapIndex;
  }

  /** set the normal map index ie the back-pointer */
  void setNormalMapIndex(int index) {
    normalMapIndex = index;
  }

  public void showInfo() {
    VM.sysWrite("  UnusualMap showInfo- ");

    VM.sysWrite("    return address index = ");
    VM.sysWrite(returnAddressIndex);
    VM.sysWrite("\n    referenceMapIndex = ");
    VM.sysWrite(referenceMapIndex);
    VM.sysWrite("\n    nonReferenceMapIndex = ");
    VM.sysWrite(nonReferenceMapIndex);
    VM.sysWrite("\n    returnAddressMapIndex = ");
    VM.sysWrite(returnAddressMapIndex);
    VM.sysWrite("\n");
  }
}
