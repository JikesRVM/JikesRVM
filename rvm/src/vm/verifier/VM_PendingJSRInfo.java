/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * This class is used when processing the JSR subroutines in bytecode
 * verification.
 * 
 * Each jsr/ret pair will have an instance of VM_PendingJSRInfo corresponding
 * to it during the processing of bytecode verification, which hold
 * information about this subroutine, such as starting type map, ending type
 * map and the registers used in this subroutine. When a ret is met, all the
 * information will be used to calculate the type map of the instruction right
 * after "jsr".
 *
 * @author Lingli Zhang   7/21/02
 */


final class VM_PendingJSRInfo {
  /** the number of successors of this subroutine */
  public int successorLength;
  /** whether this subroutine already has been processed once or not*/
  public boolean updateOnce = false;
  /** the type map right before the subroutine, by merging maps from different call sites */
  public int[] startMap;
  /** stack height at the entrance of the subroutine */
  public int startStkTop;
  /** stack height at the exit of the subroutine */
  public int endStkTop;
  /** the type map right after the subroutine */
  public int[] endMap;

  /** bytecode index of this jsr subroutine */
  private int JSRStartByteIndex;
  /** index into the map that indicates where return address is held */
  private int returnAddressLocation;
  /** an boolean array that indicates which registers are used in the subroutine */
  private boolean[] used;
  /** Basic block numbers of the successors of this subroutine */
  private short[] successorBBNums;
  /** type maps right before the successors of this subroutine */
  private int[][] successorPreMaps;
  /** if this subroutine is in another subroutine, set that subroutine as parent */
  private VM_PendingJSRInfo parent;


  /** 
   * @param JSRStartByteIndex  the bytecode index of the start of this subroutine
   * @param currBBEmpty the number of local regiters of current method
   * @param currBBMap the type map right before of this subroutine
   * @param currBBStkTop current stack height
   * @param parent not null if embedded subroutine
   */
  public VM_PendingJSRInfo(int JSRStartByteIndex, int currBBEmpty, 
                           int[] currBBMap, 
                           int currBBStkTop, VM_PendingJSRInfo parent)
  {
    this.JSRStartByteIndex = JSRStartByteIndex;
    startMap = new int[currBBMap.length];
    for (int j =0 ; j <= currBBStkTop; j++)
      startMap[j] = currBBMap[j];
    startStkTop = currBBStkTop;
    endStkTop = -1;

    used = new boolean[currBBEmpty+1];
    for (int i=0; i< used.length; i++)
      used[i] = false;
    successorBBNums = new short[10];
    successorPreMaps = new int[10][];
    successorLength = 0;
    returnAddressLocation =-1; 
    this.parent = parent;
  }

  /**
   * Set a new starting type map for this subroutine
   * @param newMap the new starting type map
   * @param newStkTop the new stack height
   */
  public void newStartMap(int[] newMap, int newStkTop) {
    if (newStkTop != startStkTop) {
      throw new IllegalArgumentException("newStkTop (" + newStkTop 
         + ") must be the same as the existing startStkTop ("
         + startStkTop + ")");
    } else {
      for (int i = 0; i <= startStkTop; i ++ )
        startMap[i] = newMap[i];
    }
  }

  /**
   * Set a new ending type map for this subroutine
   * @param newMap the new ending type map
   * @param newStkTop the new stack height
   */
  public void newEndMap(int[] newMap, int newStkTop) {

    if (endMap == null){
      endMap = new int[newMap.length];
      endStkTop = newStkTop;
      for (int i = 0; i < newStkTop; i++)
        endMap[i] = newMap[i];

    }else{
      if (newStkTop != endStkTop)
      throw new IllegalArgumentException("newStkTop (" + newStkTop 
         + ") must be the same as the existing endStkTop ("
         + endStkTop + ")");
      else
        for (int i = 0; i < newStkTop; i++)
          endMap[i] = newMap[i];
    }
  }

  /**
   * @param newLocation the index to the type map that indicates which register 
   *        holds the return address
   */
  public void updateReturnAddressLocation(int newLocation){
    returnAddressLocation = newLocation;
  }

  /** 
   * Set some register to be used
   * @param index the register's index
   */
  public void setUsed(int index){
    //VM.sysWrite("setUsed: " + index + "\n");
    used[index] = true;
  }

  /**
   * Add a basic block as this subroutine's successor
   *
   * @param preMap the type map before the subroutine at the call site
   * @param successorBBNum the basic block number of the successor
   */
  public void addSitePair(int[] preMap, short successorBBNum){

    int i;
    for (i=0; i<successorLength; i++)
      if (successorBBNums[i] == successorBBNum)
        break;

    if (i<successorLength){
      successorPreMaps[i] = preMap;
      return;
    }
    successorBBNums[successorLength] = successorBBNum;
    successorPreMaps[successorLength] = preMap;

    successorLength ++;

    if (successorLength > successorBBNums.length){ //expand the array
      short[] newBBNums = new short[successorBBNums.length +10];
      int[][] newPreMaps = new int[successorBBNums.length +10][];
      for (int j = 0; j< successorBBNums.length; j++){
        newBBNums[j] = successorBBNums[j];
        newPreMaps[j] = successorPreMaps[j];
      }
      successorBBNums = newBBNums;
      successorPreMaps = newPreMaps;
      newBBNums = null;
      newPreMaps = null;
    }
    //-------
    //VM.sysWrite("=====add " + successorBBNum + " to PendingJsrInfo " + JSRStartByteIndex + "\n"); 

  }

  public short getSuccessorBBNum(int index){
    return successorBBNums[index];
  }

  public int[] getSuccessorPreMap(int index){
    return successorPreMaps[index];
  }

  public boolean[] getUsedMap(){
    return used;
  }

  /**
   * After "ret", if this subroutine is embedded in another subroutine, 
   * add the used registers information to calling subroutine.
   */
  public void addUsedInfoToParent(){
    if (parent!= null){
      boolean[] parentUsed = parent.getUsedMap();
      if (parentUsed != null){
        for (int i=0; i<parentUsed.length; i++)
          parentUsed[i] = parentUsed[i] || used[i];
      }
    }
  }
}
