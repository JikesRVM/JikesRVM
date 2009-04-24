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
package org.jikesrvm.compilers.opt.runtimesupport;

import org.jikesrvm.VM;
import org.jikesrvm.compilers.common.ExceptionTable;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.BasicBlockEnumeration;
import org.jikesrvm.compilers.opt.ir.ExceptionHandlerBasicBlock;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.operand.TypeOperand;

/**
 * Encoding of try ranges in the final machinecode and the
 * corresponding exception type and catch block start.
 */
final class OptExceptionTable extends ExceptionTable {

  /**
   * Encode an exception table
   * @param ir the IR to encode the exception table for
   * @return the encoded exception table
   */
  static int[] encode(IR ir) {
    int index = 0;
    int currStartOff, currEndOff;
    int tableSize = countExceptionTableSize(ir);
    int[] eTable = new int[tableSize * 4];

    // For each basic block
    //   See if it has code associated with it and if it has
    //   any reachable exception handlers.
    //   When such a block is found, check the blocks that follow
    //   it in code order to see if this block has the same
    //   Bag of exceptionHandlers as any of its immediate successors.
    //   If so the try region can be expanded to include those
    //   successors. Stop checking successors as soon as a non-match
    //   is found, or a block that doesn't have handlers is found.
    //   Successors that don't have any code associated with them can
    //   be ignored.
    //   If blocks were joined together then when adding the
    //   entries to the eTable it is important to not restrict the
    //   entries to reachable handlers; as the first block may only
    //   throw a subset of the exception types represented by the Bag
    for (BasicBlock bblock = ir.firstBasicBlockInCodeOrder(); bblock != null;) {
      // Iteration is explicit in loop

      int startOff = bblock.firstInstruction().getmcOffset();
      int endOff = bblock.lastInstruction().getmcOffset();
      if (endOff > startOff) {
        if (!bblock.hasExceptionHandlers()) {
          bblock = bblock.nextBasicBlockInCodeOrder();
          continue;
        }

        BasicBlock followonBB;
        BasicBlockEnumeration reachBBe, e;
        boolean joinedBlocks;

        // First make sure at least one of the exception handlers
        // is reachable from this block
        reachBBe = bblock.getReachableExceptionHandlers();
        if (!reachBBe.hasMoreElements()) {
          bblock = bblock.nextBasicBlockInCodeOrder();
          continue;
        }

        currStartOff = startOff;
        currEndOff = endOff;
        joinedBlocks = false;

        for (followonBB = bblock.nextBasicBlockInCodeOrder(); followonBB != null; followonBB =
            followonBB.nextBasicBlockInCodeOrder()) {
          int fStartOff = followonBB.firstInstruction().getmcOffset();
          int fEndOff = followonBB.lastInstruction().getmcOffset();
          // See if followon Block has any code
          if (fEndOff > fStartOff) {
            // See if followon Block has matching handler block bag
            if (followonBB.hasExceptionHandlers() && bblock.isExceptionHandlerEquivalent(followonBB)) {
              currEndOff = fEndOff;
              joinedBlocks = true;
            } else {
              // Can't join any more blocks together
              break;
            }
          }
        }
        // found all the matching followon blocks
        // Now fill in the eTable with the handlers
        if (joinedBlocks) {
          e = bblock.getExceptionHandlers();
        } else {
          e = reachBBe;
        }

        while (e.hasMoreElements()) {
          ExceptionHandlerBasicBlock eBlock = (ExceptionHandlerBasicBlock) e.nextElement();
          for (java.util.Enumeration<TypeOperand> ets = eBlock.getExceptionTypes(); ets.hasMoreElements();) {
            TypeOperand type = ets.nextElement();
            int catchOffset = eBlock.firstInstruction().getmcOffset();
            eTable[index + TRY_START] = currStartOff;
            eTable[index + TRY_END] = currEndOff;
            eTable[index + CATCH_START] = catchOffset;
            try {
              eTable[index + EX_TYPE] = type.getTypeRef().resolve().getId();
            } catch (NoClassDefFoundError except) {
              // Yuck.  If this happens beatup Dave and make him do the right
              // thing. For now, we are forcing early loading of exception
              // types to avoid a bunch of ugly issues in resolving the type
              // when delivering the exception.  The problem is that we
              // currently can't allow a GC while in the midst of delivering
              // an exception and resolving the type reference might entail
              // calling arbitrary classloader code.
              VM.sysWriteln("Trouble resolving a caught exception at compile time:");
              except.printStackTrace(); // sysFail won't print the stack trace
              // that lead to the
              // NoClassDefFoundError.
              VM.sysFail("Unable to resolve caught exception type at compile time");
            }
            index += 4;
          }
        }
        bblock = followonBB;
      } else {
        // No code in bblock
        bblock = bblock.nextBasicBlockInCodeOrder();
      }
    }

    if (index != eTable.length) {              // resize array
      int[] newETable = new int[index];
      for (int i = 0; i < index; i++) {
        newETable[i] = eTable[i];
      }
      eTable = newETable;
    }
    return eTable;
  }

  /**
   * Return an upper bounds on the size of the exception table for an IR.
   */
  private static int countExceptionTableSize(IR ir) {
    int tSize = 0;
    for (BasicBlock bblock = ir.firstBasicBlockInCodeOrder(); bblock != null; bblock =
        bblock.nextBasicBlockInCodeOrder()) {
      if (bblock.hasExceptionHandlers()) {
        for (BasicBlockEnumeration e = bblock.getExceptionHandlers(); e.hasMoreElements();) {
          ExceptionHandlerBasicBlock ebb = (ExceptionHandlerBasicBlock) e.next();
          tSize += ebb.getNumberOfExceptionTableEntries();
        }
      }
    }
    return tSize;
  }
}



