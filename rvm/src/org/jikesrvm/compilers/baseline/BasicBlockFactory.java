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

final class BasicBlockFactory {

  private int nextBlockNumber = BasicBlock.STARTBBNUMBER;

  /**
   * This should be the usual constructor, we know the start, but don't
   * yet know the end. No predecessors.
   */
  BasicBlock newBlock(int startval) {
    int blockNumber = nextBlockNumber++;
    return new BasicBlock(startval, blockNumber);
  }

  public int getNumberofBlocks() {
    return (nextBlockNumber - 1);
  }

}
