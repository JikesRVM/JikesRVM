/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This class is used during the building of reference/nonreference maps for 
 * a method.  Once a JSR/RET combination has been processed, other JSR may 
 * be encountered that "jump" to the same subroutine. To calculate the maps 
 * of the instruction that is immediately after the JSR, we need the maps at 
 * the time of the JSR and the maps at the time of the RET.
 * 
 * @author Anthony Cocchi
 */
public final class VM_JSRSubroutineInfo {

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

  public VM_JSRSubroutineInfo(int subroutineByteCodeStart, byte[] startReferenceMap,
                              int localsTop) {
    this.subroutineByteCodeStart = subroutineByteCodeStart;
    this.startReferenceMap = new byte[localsTop+1];
    for (int i=0; i<= localsTop; i++) 
      this.startReferenceMap[i] = startReferenceMap[i];
    this.localsTop = localsTop;
    
    if (VM.ReferenceMapsStatistics) 
      JSRRoutineCount++;
  }

  public void
  newStartMaps(byte[] startReferenceMap) {
    if (VM.ReferenceMapsStatistics) {
      for (int i=0; i<= localsTop; i++) {
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

    for (int i=0; i<= localsTop; i++) 
      this.startReferenceMap[i] = startReferenceMap[i];
  }

  public void
  newEndMaps(byte[] endReferenceMap, 
             int endReferenceTop) {
    this.endReferenceMap = new byte[endReferenceTop+1];
    for (int i=0; i<= endReferenceTop; i++) 
      this.endReferenceMap[i] = endReferenceMap[i];
    this.endReferenceTop = endReferenceTop;
  }

  public byte[] 
  computeResultingMaps(int mapLength) {

    byte[] newReferenceMap = new byte[mapLength];

    // If there is no ending map, then the JSR Subroutine must have ended in
    // a return statement. Just return null
    if (endReferenceMap == null)
      return null;

    // When there is no starting non reference map, then the JSR instruction is 
    // not within another  JSR subroutine
    for (int i=0; i<=localsTop; i++) {
        if (endReferenceMap[i] == VM_BuildReferenceMaps.NOT_SET)
          newReferenceMap[i] = startReferenceMap[i];
        else {
          newReferenceMap[i] = endReferenceMap[i];
      }
    }

    // Copy over the operand stack.
    for (int i=localsTop+1; i<=endReferenceTop; i++)
      newReferenceMap[i] = endReferenceMap[i];
 
    return newReferenceMap;
  }

  /** 
   * Prints out statistics about JSR subroutines and their starting maps
   *
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
