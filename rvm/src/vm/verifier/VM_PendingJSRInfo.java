/*
 * (C) Copyright IBM Corp. 2002
 */
//$Id$

/**
 * This class is used during the processing of bytecode verification
 *
 * TODO: add javadoc comments
 *
 * @author Lingli Zhang   7/21/02
 */

class VM_PendingJSRInfo{

  public int successorLength;
  public boolean updateOnce = false;

  public int[] startMap;
  public int startStkTop;
  public int endStkTop;
  public int[] endMap;

  private int JSRStartByteIndex;
  private int returnAddressLocation;
  private boolean[] used;
  private short[] successorBBNums;
  private int[][] successorPreMaps;
  private VM_PendingJSRInfo parent;


  public VM_PendingJSRInfo(int JSRStartByteIndex, int currBBEmpty, int[] currBBMap, 
                           int currBBStkTop, VM_PendingJSRInfo parent){
    this.JSRStartByteIndex = JSRStartByteIndex;
    startMap = new int[currBBMap.length];
    for(int j =0 ; j <= currBBStkTop; j++)
      startMap[j] = currBBMap[j];
    startStkTop = currBBStkTop;
    endStkTop = -1;

    used = new boolean[currBBEmpty+1];
    for(int i=0; i< used.length; i++)
      used[i] = false;
    successorBBNums = new short[10];
    successorPreMaps = new int[10][];
    successorLength = 0;
    returnAddressLocation =-1; 
    this.parent = parent;
  }

  public void newStartMap(int[] newMap, int newStkTop) throws Exception {
    if(newStkTop != startStkTop)
      throw new Exception();
    else{
      for(int i = 0; i <= startStkTop; i ++ )
        startMap[i] = newMap[i];
    }
  }

  public void newEndMap(int[] newMap, int newStkTop) throws Exception{

    if(endMap == null){
      endMap = new int[newMap.length];
      endStkTop = newStkTop;
      for(int i = 0; i < newStkTop; i++)
        endMap[i] = newMap[i];

    }else{
      if(newStkTop != endStkTop)
        throw new Exception();
      else
        for(int i = 0; i < newStkTop; i++)
          endMap[i] = newMap[i];
    }
  }

  public void updateReturnAddressLocation(int newLocation){
    returnAddressLocation = newLocation;
  }

  public void setUsed(int index){
    //VM.sysWrite("setUsed: " + index + "\n");
    used[index] = true;
  }

  public void addSitePair(int[] preMap, short successorBBNum){

    int i;
    for(i=0; i<successorLength; i++)
      if(successorBBNums[i] == successorBBNum)
        break;

    if(i<successorLength){
      successorPreMaps[i] = preMap;
      return;
    }
    successorBBNums[successorLength] = successorBBNum;
    successorPreMaps[successorLength] = preMap;

    successorLength ++;

    if(successorLength > successorBBNums.length){ //expand the array
      short[] newBBNums = new short[successorBBNums.length +10];
      int[][] newPreMaps = new int[successorBBNums.length +10][];
      for(int j = 0; j< successorBBNums.length; j++){
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

  public void addUsedInfoToParent(){
    if(parent!= null){
      boolean[] parentUsed = parent.getUsedMap();
      if(parentUsed != null){
        for(int i=0; i<parentUsed.length; i++)
          parentUsed[i] = parentUsed[i] || used[i];
      }
    }
  }
}
