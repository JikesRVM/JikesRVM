/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * @author Perry Cheng
 */
public final class VM_BasicBlockFactory {

  private int nextBlockNumber = VM_BasicBlock.STARTBBNUMBER;

 // This should be the usual constructor, we know the start, but don't
 // yet know the end. No predecessors.
 VM_BasicBlock newBlock (int startval) {
   int blockNumber = nextBlockNumber ++;
   return new VM_BasicBlock(startval, blockNumber);
 }

 public int getNumberofBlocks() {
   return (nextBlockNumber -1);
 }

}
