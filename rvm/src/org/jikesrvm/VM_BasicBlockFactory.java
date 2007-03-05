/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm;

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
