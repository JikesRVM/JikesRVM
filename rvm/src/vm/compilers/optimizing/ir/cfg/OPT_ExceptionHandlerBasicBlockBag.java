/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt.ir;

import com.ibm.JikesRVM.opt.OPT_OptimizingCompilerException;

/**
 * A container for the chain of exception handlers for a basic block.
 * 
 * @author Dave Grove
 * @modified by Steve Fink
 *
 * @see OPT_BasicBlock
 * @see OPT_ExceptionHandlerBasicBlock
 *
 */
final class OPT_ExceptionHandlerBasicBlockBag {

  /**
   * The array of ExceptionHandlerBasicBlocks constructed by BC2IR
   * based on the local set of handlers visible within a single method
   */
  private OPT_ExceptionHandlerBasicBlock[] local;

  /**
   * If this is an inlined method, then this points to the enclosing
   * method's (the caller's) ExcpetionHandlerBasicBlockBag.  If this is 
   * the outermost method, then this is null
   */
  private OPT_ExceptionHandlerBasicBlockBag caller;

  /**
   * only for use by BC2IR; return {@link #caller}
   * @return the contents of {@link #caller}
   */
  OPT_ExceptionHandlerBasicBlockBag getCaller() {
    return caller;
  }

  /**
   * Create an EHBBB 
   * @param l the local array of EHBBs
   * @param c the enclosing EHBBB
   */
  OPT_ExceptionHandlerBasicBlockBag(OPT_ExceptionHandlerBasicBlock[] l, 
                                    OPT_ExceptionHandlerBasicBlockBag c) {
    local = l;
    caller = c;
  }

    /**
     * take an element out f the bag.  Throw an exception if the block
     * to remove is not in the bag
     */
    public void remove(OPT_BasicBlock bb) {
        for(int i = 0; i < local.length; i++) {
            if (bb == local[i]) {
                OPT_ExceptionHandlerBasicBlock[] newLocal =
                    new OPT_ExceptionHandlerBasicBlock[ local.length - 1 ];
               
                for(int j = 0; j < i; j++) newLocal[j] = local[j];

                for(int j = i+1; j < local.length; j++) newLocal[j-1]=local[j];

                local = newLocal;
                return;
            }
        }

        throw new OPT_OptimizingCompilerException("Removing block not present in bag: " + bb);
    }

  /**
   * An enumeration of all the exception handler basic blocks 
   * (transitively) in the EHBBB.
   * @return An enumeration of the exception handler basic blocks in the bag.
   */
  public OPT_BasicBlockEnumeration enumerator() {
    return new OPT_BasicBlockEnumeration() {
      private int cur_idx = 0;
      private OPT_ExceptionHandlerBasicBlockBag cur_bag = null;
      // Initialize enumeration to point to first ehbb (if any)
      {
        OPT_ExceptionHandlerBasicBlockBag c = OPT_ExceptionHandlerBasicBlockBag.this;
        while (c != null && (c.local == null || c.local.length == 0)) { c = c.caller; }
        if (c != null) {
          cur_bag = c;
        }
      }
      public boolean hasMoreElements() { return cur_bag != null; }
      public Object nextElement() { return next(); }
      public OPT_BasicBlock next() {
        OPT_ExceptionHandlerBasicBlock ans;
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

