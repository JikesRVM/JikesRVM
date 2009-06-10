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
 * This class is used during the building of reference/non-reference maps for
 * a method.  Once a JSR/RET combination has been processed, other JSR may
 * be encountered that "jump" to the same subroutine. To calculate the maps
 * of the instruction that is immediately after the JSR, we need the maps at
 * the time of the JSR and the maps at the time of the RET.
 */
final class JSRSubroutineInfo {
  public int subroutineByteCodeStart;
  public byte[] startReferenceMap;
  int localsTop;
  public byte[] endReferenceMap;
  public int endReferenceTop;

  // for statistics
  private static int JSRRoutineCount;
  private static int JSRMismatchCount;  // count of jsr's that have different starting maps
  private static int JSRRoutinesWithMismatch;
  private boolean hasMismatch;

  public JSRSubroutineInfo(int subroutineByteCodeStart, byte[] startReferenceMap, int localsTop) {
    this.subroutineByteCodeStart = subroutineByteCodeStart;
    this.startReferenceMap = new byte[localsTop + 1];
    for (int i = 0; i <= localsTop; i++) {
      this.startReferenceMap[i] = startReferenceMap[i];
    }
    this.localsTop = localsTop;

    if (VM.ReferenceMapsStatistics) {
      JSRRoutineCount++;
    }
  }

  public void newStartMaps(byte[] startReferenceMap) {
    if (VM.ReferenceMapsStatistics) {
      for (int i = 0; i <= localsTop; i++) {
        if (this.startReferenceMap[i] != startReferenceMap[i]) {
          if (!hasMismatch) {
            hasMismatch = true;
            JSRRoutinesWithMismatch++;
          }
          JSRMismatchCount++;
          break;
        }
      }
    }

    for (int i = 0; i <= localsTop; i++) {
      this.startReferenceMap[i] = startReferenceMap[i];
    }
  }

  public void newEndMaps(byte[] endReferenceMap, int endReferenceTop) {
    this.endReferenceMap = new byte[endReferenceTop + 1];
    for (int i = 0; i <= endReferenceTop; i++) {
      this.endReferenceMap[i] = endReferenceMap[i];
    }
    this.endReferenceTop = endReferenceTop;
  }

  public byte[] computeResultingMaps(int mapLength) {

    byte[] newReferenceMap = new byte[mapLength];

    // If there is no ending map, then the JSR Subroutine must have ended in
    // a return statement. Just return null
    if (endReferenceMap == null) {
      return null;
    }

    // When there is no starting non reference map, then the JSR instruction is
    // not within another  JSR subroutine
    for (int i = 0; i <= localsTop; i++) {
      if (endReferenceMap[i] == BuildReferenceMaps.NOT_SET) {
        newReferenceMap[i] = startReferenceMap[i];
      } else {
        newReferenceMap[i] = endReferenceMap[i];
      }
    }

    // Copy over the operand stack.
    for (int i = localsTop + 1; i <= endReferenceTop; i++) {
      newReferenceMap[i] = endReferenceMap[i];
    }

    return newReferenceMap;
  }

  /**
   * Prints out statistics about JSR subroutines and their starting maps
   */
  public static void printStatistics() {
    VM.sysWrite("Number of JSR Subroutines processed: ");
    VM.sysWrite(JSRRoutineCount);
    VM.sysWrite("\n");
    VM.sysWrite("Number of JSR Subroutines that started with a mismatched map: ");
    VM.sysWrite(JSRRoutinesWithMismatch);
    VM.sysWrite("\n");
    VM.sysWrite("Total number of mismatch starts encountered :");
    VM.sysWrite(JSRMismatchCount);
  }
}
