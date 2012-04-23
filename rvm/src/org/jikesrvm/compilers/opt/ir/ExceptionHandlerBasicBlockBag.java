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
package org.jikesrvm.compilers.opt.ir;

import org.jikesrvm.compilers.opt.OptimizingCompilerException;

/**
 * A container for the chain of exception handlers for a basic block.
 *
 *
 * @see BasicBlock
 * @see ExceptionHandlerBasicBlock
 */
public final class ExceptionHandlerBasicBlockBag {

  /**
   * The array of ExceptionHandlerBasicBlocks constructed by BC2IR
   * based on the local set of handlers visible within a single method
   */
  private ExceptionHandlerBasicBlock[] local;

  /**
   * If this is an inlined method, then this points to the enclosing
   * method's (the caller's) ExcpetionHandlerBasicBlockBag.  If this is
   * the outermost method, then this is null
   */
  private final ExceptionHandlerBasicBlockBag caller;

  /**
   * only for use by BC2IR; return {@link #caller}
   * @return the contents of {@link #caller}
   */
  public ExceptionHandlerBasicBlockBag getCaller() {
    return caller;
  }

  /**
   * Create an EHBBB
   * @param l the local array of EHBBs
   * @param c the enclosing EHBBB
   */
  public ExceptionHandlerBasicBlockBag(ExceptionHandlerBasicBlock[] l, ExceptionHandlerBasicBlockBag c) {
    local = l;
    caller = c;
  }

  /**
   * take an element out f the bag.  Throw an exception if the block
   * to remove is not in the bag
   */
  public void remove(BasicBlock bb) {
    for (int i = 0; i < local.length; i++) {
      if (bb == local[i]) {
        ExceptionHandlerBasicBlock[] newLocal = new ExceptionHandlerBasicBlock[local.length - 1];

        for (int j = 0; j < i; j++) newLocal[j] = local[j];

        for (int j = i + 1; j < local.length; j++) newLocal[j - 1] = local[j];

        local = newLocal;
        return;
      }
    }

    throw new OptimizingCompilerException("Removing block not present in bag: " + bb);
  }

  /**
   * An enumeration of all the exception handler basic blocks
   * (transitively) in the EHBBB.
   * @return An enumeration of the exception handler basic blocks in the bag.
   */
  public BasicBlockEnumeration enumerator() {
    return new BasicBlockEnumeration() {
      private int cur_idx = 0;
      private ExceptionHandlerBasicBlockBag cur_bag = null;

      // Initialize enumeration to point to first ehbb (if any)
      {
        ExceptionHandlerBasicBlockBag c = ExceptionHandlerBasicBlockBag.this;
        while (c != null && (c.local == null || c.local.length == 0)) { c = c.caller; }
        if (c != null) {
          cur_bag = c;
        }
      }

      @Override
      public boolean hasMoreElements() { return cur_bag != null; }

      @Override
      public BasicBlock nextElement() { return next(); }

      @Override
      public BasicBlock next() {
        ExceptionHandlerBasicBlock ans;
        try {
          ans = cur_bag.local[cur_idx++];
        } catch (NullPointerException e) {
          throw new java.util.NoSuchElementException();
        }
        // Now advance state to point to next element.
        if (cur_idx == cur_bag.local.length) {
          cur_bag = cur_bag.caller;
          while (cur_bag != null && (cur_bag.local == null || cur_bag.local.length == 0)) {
            cur_bag = cur_bag.caller;
          }
          if (cur_bag != null) {
            cur_idx = 0; // found the next array, reset idx to first element.
          }
        }
        return ans;
      }
    };
  }
}

